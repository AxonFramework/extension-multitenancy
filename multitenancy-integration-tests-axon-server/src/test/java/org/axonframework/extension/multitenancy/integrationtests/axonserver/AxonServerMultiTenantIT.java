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
package org.axonframework.extension.multitenancy.integrationtests.axonserver;

import org.awaitility.Awaitility;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.extension.multitenancy.core.MetadataBasedTenantResolver;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.core.TenantConnectPredicate;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.core.configuration.MultiTenancyConfigurer;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.read.coursestats.FindAllCourses;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.read.coursestats.CourseStatsConfiguration;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.read.coursestats.CourseStatsRepository;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.read.coursestats.CoursesStats;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.read.coursestats.InMemoryCourseStatsRepository;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.shared.CourseId;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.write.createcourse.CreateCourse;
import org.axonframework.extension.multitenancy.integrationtests.axonserver.write.createcourse.CreateCourseConfiguration;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration tests verifying multi-tenant infrastructure with Axon Server.
 * <p>
 * <b>Important:</b> Without an Axon Server license, only the {@code default} context is available.
 * These tests verify the full command → event → projection → query flow works through the
 * multi-tenant infrastructure connected to Axon Server.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
@Testcontainers
class AxonServerMultiTenantIT {

    private static final String AXON_SERVER_IMAGE = "docker.axoniq.io/axoniq/axonserver:2025.2.0";
    private static final TenantDescriptor DEFAULT_TENANT = TenantDescriptor.tenantWithId("default");

    @Container
    static final AxonServerContainer axonServer = new AxonServerContainer(AXON_SERVER_IMAGE)
            .withDevMode(true)
            .withDcbContext(true)
            .withReuse(true);

    private AxonConfiguration configuration;
    private CommandGateway commandGateway;
    private QueryGateway queryGateway;

    @BeforeAll
    static void beforeAll() {
        assertThat(axonServer.isRunning()).isTrue();
    }

    @AfterEach
    void tearDown() {
        if (configuration != null) {
            configuration.shutdown();
            configuration = null;
        }
    }

    private void startApp() {
        AxonServerConfiguration asConfig = new AxonServerConfiguration();
        asConfig.setServers(axonServer.getAxonServerAddress());

        EventSourcingConfigurer configurer = EventSourcingConfigurer.create();

        configurer.componentRegistry(cr -> cr.registerComponent(
                AxonServerConfiguration.class, c -> asConfig));

        configurer.componentRegistry(cr -> cr.registerComponent(
                TenantConnectPredicate.class,
                c -> tenant -> "default".equals(tenant.tenantId())));

        MultiTenancyConfigurer multiTenancyConfigurer = MultiTenancyConfigurer.enhance(configurer)
                .registerTargetTenantResolver(config -> new MetadataBasedTenantResolver());

        configurer = CreateCourseConfiguration.configure(configurer);
        configurer = CourseStatsConfiguration.configure(configurer);

        multiTenancyConfigurer.tenantComponent(CourseStatsRepository.class, tenant -> new InMemoryCourseStatsRepository());

        configurer.messaging(mc -> mc.registerCorrelationDataProvider(config -> message -> {
            Map<String, String> result = new HashMap<>();
            if (message.metadata().containsKey("tenantId")) {
                result.put("tenantId", message.metadata().get("tenantId"));
            }
            return result;
        }));

        configuration = configurer.start();
        commandGateway = configuration.getComponent(CommandGateway.class);
        queryGateway = configuration.getComponent(QueryGateway.class);
    }

    private void waitForTenantRegistration() {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    TenantProvider provider = configuration.getComponent(TenantProvider.class);
                    return provider.getTenants().contains(DEFAULT_TENANT);
                });
    }

    private void createCourseForTenant(TenantDescriptor tenant, CourseId courseId, String name, int capacity) {
        commandGateway.send(
                new CreateCourse(courseId, name, capacity),
                Metadata.with("tenantId", tenant.tenantId()),
                null
        ).getResultMessage().join();
    }

    private List<CoursesStats> queryCoursesForTenant(TenantDescriptor tenant) {
        QueryMessage query = new GenericQueryMessage(
                new MessageType(FindAllCourses.class), new FindAllCourses()
        ).andMetadata(Metadata.with("tenantId", tenant.tenantId()));
        return queryGateway.query(query, FindAllCourses.Result.class)
                .thenApply(FindAllCourses.Result::courses).join();
    }

    @Test
    void commandCreatesEventWhichUpdatesProjection() {
        startApp();
        waitForTenantRegistration();

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
                        .anyMatch(c -> c.courseId().equals(courseId)));

        List<CoursesStats> courses = queryCoursesForTenant(DEFAULT_TENANT);
        assertThat(courses)
                .anyMatch(c -> c.courseId().equals(courseId) && c.name().equals(courseName));
    }

    @Test
    void multipleCoursesCanBeCreatedAndQueried() {
        startApp();
        waitForTenantRegistration();

        CourseId course1 = CourseId.random();
        CourseId course2 = CourseId.random();
        String name1 = "Course One " + System.currentTimeMillis();
        String name2 = "Course Two " + System.currentTimeMillis();

        createCourseForTenant(DEFAULT_TENANT, course1, name1, 30);
        createCourseForTenant(DEFAULT_TENANT, course2, name2, 25);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    List<CoursesStats> courses = queryCoursesForTenant(DEFAULT_TENANT);
                    return courses.stream().anyMatch(c -> c.courseId().equals(course1))
                            && courses.stream().anyMatch(c -> c.courseId().equals(course2));
                });

        List<CoursesStats> courses = queryCoursesForTenant(DEFAULT_TENANT);
        assertThat(courses).anyMatch(c -> c.courseId().equals(course1) && c.name().equals(name1));
        assertThat(courses).anyMatch(c -> c.courseId().equals(course2) && c.name().equals(name2));
    }

    @Test
    void commandToNonExistentTenantFails() {
        startApp();
        waitForTenantRegistration();

        TenantDescriptor nonExistentTenant = TenantDescriptor.tenantWithId("non-existent");
        CourseId courseId = CourseId.random();

        assertThatThrownBy(() ->
                createCourseForTenant(nonExistentTenant, courseId, "Should Fail", 10)
        ).hasCauseInstanceOf(NoSuchTenantException.class);
    }

    @Test
    void commandWithoutTenantMetadataFails() {
        startApp();
        waitForTenantRegistration();

        CourseId courseId = CourseId.random();
        CreateCourse command = new CreateCourse(courseId, "Test Course without Tenant", 10);

        assertThatThrownBy(() -> commandGateway.sendAndWait(command))
                .isInstanceOf(NoSuchTenantException.class);
    }
}
