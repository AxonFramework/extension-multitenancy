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
package org.axonframework.extension.multitenancy.integrationtests.springboot.embedded;

import org.awaitility.Awaitility;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.read.coursestats.CourseStatsReadModel;
import org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.read.coursestats.FindAllCourses;
import org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.shared.TenantAuditService;
import org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.shared.CourseId;
import org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.write.createcourse.CreateCourse;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.config.TestMultiTenancyConfiguration.TENANT_A;
import static org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.config.TestMultiTenancyConfiguration.TENANT_B;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Spring Boot integration tests verifying multi-tenant isolation with JPA repositories.
 * <p>
 * Key differences from the embedded integration tests:
 * <ul>
 *     <li>Uses Spring Boot autoconfiguration (not raw Configurer)</li>
 *     <li>Handlers are auto-discovered via component scanning</li>
 *     <li>Spring Data JPA repositories are automatically tenant-scoped via
 *         {@link org.axonframework.extension.multitenancy.spring.data.TenantRepositoryParameterResolverFactory}</li>
 * </ul>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SpringBootEmbeddedMultiTenantIT {

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private QueryGateway queryGateway;

    @BeforeEach
    void setUp() {
        TenantAuditService.clearAllAuditEntries();
    }

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
    void tenantScopedRepositoryInjection_repositorySavesToCorrectTenantDatabase() {
        CourseId courseIdA = CourseId.random();
        CourseId courseIdB = CourseId.random();

        // Create courses in different tenants
        createCourseForTenant(TENANT_A, courseIdA, "Spring Boot Course A", 25);
        createCourseForTenant(TENANT_B, courseIdB, "Spring Boot Course B", 35);

        // Wait for projections to process
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                !queryCoursesForTenant(TENANT_A).isEmpty() && !queryCoursesForTenant(TENANT_B).isEmpty()
        );

        // Verify tenant A only sees their course
        List<CourseStatsReadModel> tenantACourses = queryCoursesForTenant(TENANT_A);
        assertThat(tenantACourses).hasSize(1);
        assertThat(tenantACourses.get(0).getCourseId()).isEqualTo(courseIdA.raw());
        assertThat(tenantACourses.get(0).getName()).isEqualTo("Spring Boot Course A");

        // Verify tenant B only sees their course
        List<CourseStatsReadModel> tenantBCourses = queryCoursesForTenant(TENANT_B);
        assertThat(tenantBCourses).hasSize(1);
        assertThat(tenantBCourses.get(0).getCourseId()).isEqualTo(courseIdB.raw());
        assertThat(tenantBCourses.get(0).getName()).isEqualTo("Spring Boot Course B");
    }

    @Test
    void fullTenantIsolation_dataFromTenantANotVisibleInTenantB() {
        CourseId courseIdA1 = CourseId.random();
        CourseId courseIdA2 = CourseId.random();
        CourseId courseIdB = CourseId.random();

        // Create two courses for tenant A
        createCourseForTenant(TENANT_A, courseIdA1, "First Course TenantA", 10);
        createCourseForTenant(TENANT_A, courseIdA2, "Second Course TenantA", 15);

        // Create one course for tenant B
        createCourseForTenant(TENANT_B, courseIdB, "Only Course TenantB", 20);

        // Wait for projections
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            List<CourseStatsReadModel> a = queryCoursesForTenant(TENANT_A);
            List<CourseStatsReadModel> b = queryCoursesForTenant(TENANT_B);
            return a.size() == 2 && b.size() == 1;
        });

        // Tenant A sees exactly 2 courses
        List<CourseStatsReadModel> tenantACourses = queryCoursesForTenant(TENANT_A);
        assertThat(tenantACourses).hasSize(2);
        assertThat(tenantACourses).extracting(CourseStatsReadModel::getName)
                .containsExactlyInAnyOrder("First Course TenantA", "Second Course TenantA");

        // Tenant B sees exactly 1 course - NOT tenant A's courses
        List<CourseStatsReadModel> tenantBCourses = queryCoursesForTenant(TENANT_B);
        assertThat(tenantBCourses).hasSize(1);
        assertThat(tenantBCourses.get(0).getName()).isEqualTo("Only Course TenantB");
    }

    @Test
    void sameEntityIdInDifferentTenants_noConflicts() {
        // Use same CourseId in both tenants
        CourseId sharedCourseId = CourseId.random();

        // Create in tenant A
        assertDoesNotThrow(() ->
                createCourseForTenant(TENANT_A, sharedCourseId, "Shared ID Course A", 30)
        );

        // Create same ID in tenant B - should succeed (different tenant database)
        assertDoesNotThrow(() ->
                createCourseForTenant(TENANT_B, sharedCourseId, "Shared ID Course B", 40)
        );

        // Wait and verify both exist independently
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                !queryCoursesForTenant(TENANT_A).isEmpty() && !queryCoursesForTenant(TENANT_B).isEmpty()
        );

        List<CourseStatsReadModel> tenantACourses = queryCoursesForTenant(TENANT_A);
        List<CourseStatsReadModel> tenantBCourses = queryCoursesForTenant(TENANT_B);

        // Both tenants have a course with the same ID but different data
        assertThat(tenantACourses).hasSize(1);
        assertThat(tenantBCourses).hasSize(1);
        assertThat(tenantACourses.get(0).getCourseId()).isEqualTo(sharedCourseId.raw());
        assertThat(tenantBCourses.get(0).getCourseId()).isEqualTo(sharedCourseId.raw());
        assertThat(tenantACourses.get(0).getName()).isEqualTo("Shared ID Course A");
        assertThat(tenantBCourses.get(0).getName()).isEqualTo("Shared ID Course B");
    }

    @Test
    void commandWithoutTenantMetadata_failsWithNoSuchTenantException() {
        CourseId courseId = CourseId.random();
        CreateCourse command = new CreateCourse(courseId, "Test Course No Tenant", 10);

        // Send without tenant metadata - should fail
        assertThatThrownBy(() -> commandGateway.sendAndWait(command))
                .isInstanceOf(NoSuchTenantException.class);
    }

    @Test
    void queryWithoutTenantMetadata_failsWithNoSuchTenantException() {
        // First create some data
        createCourseForTenant(TENANT_A, CourseId.random(), "Course for QueryTest", 15);

        // Wait for projection
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                !queryCoursesForTenant(TENANT_A).isEmpty()
        );

        // Query without tenant metadata
        QueryMessage query = new GenericQueryMessage(
                new MessageType(FindAllCourses.class), new FindAllCourses()
        );
        // No tenant metadata added

        assertThatThrownBy(() -> queryGateway.query(query, FindAllCourses.Result.class).join())
                .hasCauseInstanceOf(NoSuchTenantException.class);
    }

    @Test
    void tenantComponentInjection_serviceReceivesCorrectTenantContext() {
        CourseId courseIdA = CourseId.random();
        CourseId courseIdB = CourseId.random();

        // Create courses in different tenants
        createCourseForTenant(TENANT_A, courseIdA, "TenantComponent Test A", 10);
        createCourseForTenant(TENANT_B, courseIdB, "TenantComponent Test B", 20);

        // Wait for projections to process (which triggers audit recording)
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                TenantAuditService.getAllAuditEntries().size() >= 2
        );

        // Verify tenant A's audit service recorded with correct tenant context
        var tenantAEntries = TenantAuditService.getAuditEntriesForTenant(TENANT_A.tenantId());
        assertThat(tenantAEntries).hasSize(1);
        assertThat(tenantAEntries.get(0).action()).contains(courseIdA.raw());

        // Verify tenant B's audit service recorded with correct tenant context
        var tenantBEntries = TenantAuditService.getAuditEntriesForTenant(TENANT_B.tenantId());
        assertThat(tenantBEntries).hasSize(1);
        assertThat(tenantBEntries.get(0).action()).contains(courseIdB.raw());

        // Verify no cross-tenant contamination
        assertThat(tenantAEntries.get(0).action()).doesNotContain(courseIdB.raw());
        assertThat(tenantBEntries.get(0).action()).doesNotContain(courseIdA.raw());

        // Verify Spring DI worked - Clock was injected and timestamps are present
        assertThat(tenantAEntries.get(0).timestamp()).isNotNull();
        assertThat(tenantBEntries.get(0).timestamp()).isNotNull();
    }

    @Test
    void tenantComponentIsolation_eachTenantGetsOwnServiceInstance() {
        // Create multiple courses for tenant A
        CourseId courseIdA1 = CourseId.random();
        CourseId courseIdA2 = CourseId.random();
        createCourseForTenant(TENANT_A, courseIdA1, "Course A1", 10);
        createCourseForTenant(TENANT_A, courseIdA2, "Course A2", 15);

        // Create one course for tenant B
        CourseId courseIdB = CourseId.random();
        createCourseForTenant(TENANT_B, courseIdB, "Course B", 20);

        // Wait for all projections
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                TenantAuditService.getAllAuditEntries().size() >= 3
        );

        // Tenant A should have 2 audit entries
        var tenantAEntries = TenantAuditService.getAuditEntriesForTenant(TENANT_A.tenantId());
        assertThat(tenantAEntries).hasSize(2);

        // Tenant B should have 1 audit entry
        var tenantBEntries = TenantAuditService.getAuditEntriesForTenant(TENANT_B.tenantId());
        assertThat(tenantBEntries).hasSize(1);

        // All entries for tenant A should have tenant A's ID
        assertThat(tenantAEntries).allMatch(e -> e.tenantId().equals(TENANT_A.tenantId()));

        // All entries for tenant B should have tenant B's ID
        assertThat(tenantBEntries).allMatch(e -> e.tenantId().equals(TENANT_B.tenantId()));
    }
}
