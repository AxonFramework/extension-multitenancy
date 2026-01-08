/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver;

import org.awaitility.Awaitility;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.read.coursestats.CourseStatsReadModel;
import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.read.coursestats.FindAllCourses;
import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.shared.CourseId;
import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.write.createcourse.CreateCourse;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.config.TestMultiTenancyConfiguration.DEFAULT_TENANT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Spring Boot integration tests verifying multi-tenant infrastructure with Axon Server.
 * <p>
 * <b>Important:</b> Without an Axon Server license, only the {@code default} context is available.
 * These tests verify the full command → event → projection → query flow works through the
 * multi-tenant infrastructure connected to Axon Server with Spring Boot autoconfiguration.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@Testcontainers
class SpringBootAxonServerMultiTenantIT {

    private static final String AXON_SERVER_IMAGE = "docker.axoniq.io/axoniq/axonserver:2025.2.0";

    @Container
    static final AxonServerContainer axonServer = new AxonServerContainer(AXON_SERVER_IMAGE)
            .withDevMode(true)
            .withDcbContext(true)
            .withReuse(true);

    @DynamicPropertySource
    static void axonServerProperties(DynamicPropertyRegistry registry) {
        registry.add("axon.axonserver.servers", axonServer::getAxonServerAddress);
    }

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private QueryGateway queryGateway;

    private void createCourseForTenant(TenantDescriptor tenant, CourseId courseId, String name, int capacity) {
        commandGateway.send(
                new CreateCourse(courseId, name, capacity),
                Metadata.with("tenantId", tenant.tenantId()),
                null
        ).getResultMessage().join();
    }

    private List<CourseStatsReadModel> queryCoursesForTenant(TenantDescriptor tenant) {
        QueryMessage query = new GenericQueryMessage(
                new MessageType(FindAllCourses.class), new FindAllCourses()
        ).andMetadata(Metadata.with("tenantId", tenant.tenantId()));
        return queryGateway.query(query, FindAllCourses.Result.class)
                .thenApply(FindAllCourses.Result::courses).join();
    }

    @Test
    void commandCreatesEventWhichUpdatesProjection() {
        CourseId courseId = CourseId.random();
        String courseName = "Test Course " + System.currentTimeMillis();

        // Send command
        assertDoesNotThrow(() ->
                createCourseForTenant(DEFAULT_TENANT, courseId, courseName, 50)
        );

        // Wait for projection to process and verify
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> queryCoursesForTenant(DEFAULT_TENANT).stream()
                        .anyMatch(c -> c.getCourseId().equals(courseId.raw())));

        List<CourseStatsReadModel> courses = queryCoursesForTenant(DEFAULT_TENANT);
        assertThat(courses)
                .anyMatch(c -> c.getCourseId().equals(courseId.raw()) && c.getName().equals(courseName));
    }

    @Test
    void multipleCoursesCanBeCreatedAndQueried() {
        CourseId course1 = CourseId.random();
        CourseId course2 = CourseId.random();
        String name1 = "Course One " + System.currentTimeMillis();
        String name2 = "Course Two " + System.currentTimeMillis();

        createCourseForTenant(DEFAULT_TENANT, course1, name1, 30);
        createCourseForTenant(DEFAULT_TENANT, course2, name2, 25);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    List<CourseStatsReadModel> courses = queryCoursesForTenant(DEFAULT_TENANT);
                    return courses.stream().anyMatch(c -> c.getCourseId().equals(course1.raw()))
                            && courses.stream().anyMatch(c -> c.getCourseId().equals(course2.raw()));
                });

        List<CourseStatsReadModel> courses = queryCoursesForTenant(DEFAULT_TENANT);
        assertThat(courses).anyMatch(c -> c.getCourseId().equals(course1.raw()) && c.getName().equals(name1));
        assertThat(courses).anyMatch(c -> c.getCourseId().equals(course2.raw()) && c.getName().equals(name2));
    }

    @Test
    void commandToNonExistentTenantFails() {
        TenantDescriptor nonExistentTenant = TenantDescriptor.tenantWithId("non-existent");
        CourseId courseId = CourseId.random();

        assertThatThrownBy(() ->
                createCourseForTenant(nonExistentTenant, courseId, "Should Fail", 10)
        ).hasCauseInstanceOf(NoSuchTenantException.class);
    }

    @Test
    void commandWithoutTenantMetadataFails() {
        CourseId courseId = CourseId.random();
        CreateCourse command = new CreateCourse(courseId, "Test Course without Tenant", 10);

        assertThatThrownBy(() -> commandGateway.sendAndWait(command))
                .isInstanceOf(NoSuchTenantException.class);
    }
}
