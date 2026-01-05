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
package org.axonframework.extension.multitenancy.integrationtests.embedded;

import org.awaitility.Awaitility;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.InterceptingEventStore;
import org.axonframework.extension.multitenancy.core.MetadataBasedTenantResolver;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.core.SimpleTenantProvider;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.core.configuration.MultiTenancyConfigurer;
import org.axonframework.extension.multitenancy.eventsourcing.eventstore.MultiTenantEventStore;
import org.axonframework.extension.multitenancy.integrationtests.embedded.read.coursestats.FindAllCourses;
import org.axonframework.extension.multitenancy.integrationtests.embedded.read.coursestats.CoursesStats;
import org.axonframework.extension.multitenancy.integrationtests.embedded.read.coursestats.CourseStatsConfiguration;
import org.axonframework.extension.multitenancy.integrationtests.embedded.read.coursestats.CourseStatsRepository;
import org.axonframework.extension.multitenancy.integrationtests.embedded.read.coursestats.InMemoryCourseStatsRepository;
import org.axonframework.extension.multitenancy.integrationtests.embedded.shared.CourseId;
import org.axonframework.extension.multitenancy.integrationtests.embedded.write.createcourse.CreateCourse;
import org.axonframework.extension.multitenancy.integrationtests.embedded.write.createcourse.CreateCourseConfiguration;
import org.axonframework.extension.multitenancy.integrationtests.embedded.automation.CourseNotificationConfiguration;
import org.axonframework.extension.multitenancy.integrationtests.embedded.automation.NotificationService;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.axonframework.messaging.core.interception.DispatchInterceptorRegistry;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration tests verifying multi-tenant isolation with embedded in-memory event store.
 * <p>
 * These tests verify the core multi-tenancy functionality:
 * <ul>
 *     <li>Tenant isolation - data from tenant A not visible in tenant B</li>
 *     <li>Tenant routing - commands/queries routed to correct tenant segment</li>
 *     <li>Dynamic tenant registration - tenants can be added after startup</li>
 * </ul>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
class EmbeddedMultiTenantIT {

    private static final TenantDescriptor TENANT_A = TenantDescriptor.tenantWithId("tenant-a");
    private static final TenantDescriptor TENANT_B = TenantDescriptor.tenantWithId("tenant-b");

    private SimpleTenantProvider tenantProvider;
    private AxonConfiguration configuration;
    private CommandGateway commandGateway;
    private QueryGateway queryGateway;

    @AfterEach
    void tearDown() {
        if (configuration != null) {
            configuration.shutdown();
            configuration = null;
        }
    }

    private void startApp() {
        tenantProvider = new SimpleTenantProvider(List.of(TENANT_A, TENANT_B));

        // Create base configurer
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create();

        // Enhance with multi-tenancy - this registers resolver factories early
        MultiTenancyConfigurer multiTenancyConfigurer = MultiTenancyConfigurer.enhance(configurer)
                .registerTenantProvider(config -> tenantProvider)
                .registerTargetTenantResolver(config -> new MetadataBasedTenantResolver());

        // Domain modules can be configured in ANY order relative to tenantComponent()
        // because resolver factories are registered in enhance(), not tenantComponent()
        configurer = CreateCourseConfiguration.configure(configurer);
        configurer = CourseStatsConfiguration.configure(configurer);

        // tenantComponent() can be called after domain modules are configured
        multiTenancyConfigurer.tenantComponent(CourseStatsRepository.class, tenant -> new InMemoryCourseStatsRepository());

        // Register correlation provider for metadata-based tenant resolution
        // This ensures tenantId propagates from commands to events
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
    void debugCorrelationAndInterceptorConfiguration() {
        // Modified startApp with debug hooks
        tenantProvider = new SimpleTenantProvider(List.of(TENANT_A, TENANT_B));
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create();
        configurer = CreateCourseConfiguration.configure(configurer);
        configurer = CourseStatsConfiguration.configure(configurer);

        configurer.messaging(mc -> mc.registerCorrelationDataProvider(config -> message -> {
            Map<String, String> result = new HashMap<>();
            if (message.metadata().containsKey("tenantId")) {
                result.put("tenantId", message.metadata().get("tenantId"));
            }
            return result;
        }));

        MultiTenancyConfigurer.enhance(configurer)
                .registerTenantProvider(config -> tenantProvider)
                .registerTargetTenantResolver(config -> new MetadataBasedTenantResolver())
                .tenantComponent(CourseStatsRepository.class, tenant -> new InMemoryCourseStatsRepository());

        // Debug: Check if MultiTenancy enhancer actually runs by adding decorator at MIN_VALUE-1 (BEFORE MultiTenant)
        configurer.componentRegistry(cr -> cr.registerDecorator(
                EventStore.class,
                Integer.MIN_VALUE + 10, // Between MIN_VALUE and +50 to see what's happening
                (config, name, delegate) -> {
                    System.out.println("=== DEBUG @ MIN_VALUE+10 ===");
                    System.out.println("  delegate type: " + delegate.getClass().getName());
                    return delegate;
                }
        ));

        // Debug: right after MultiTenantEventStore decorator (MIN_VALUE)
        configurer.componentRegistry(cr -> cr.registerDecorator(
                EventStore.class,
                Integer.MIN_VALUE + 1,
                (config, name, delegate) -> {
                    System.out.println("=== DEBUG @ MIN_VALUE+1 (after MultiTenant) ===");
                    System.out.println("  delegate type: " + delegate.getClass().getName());
                    return delegate;
                }
        ));

        // Debug: right before InterceptingEventStore decorator (MIN_VALUE + 50)
        configurer.componentRegistry(cr -> cr.registerDecorator(
                EventStore.class,
                Integer.MIN_VALUE + 49,
                (config, name, delegate) -> {
                    System.out.println("=== DEBUG @ MIN_VALUE+49 (before Intercepting) ===");
                    System.out.println("  delegate type: " + delegate.getClass().getName());
                    DispatchInterceptorRegistry registry = config.getComponent(DispatchInterceptorRegistry.class);
                    List<MessageDispatchInterceptor<? super EventMessage>> interceptors = registry.eventInterceptors(config);
                    System.out.println("  Event dispatch interceptors: " + interceptors.size());
                    return delegate;
                }
        ));

        // Debug: right after InterceptingEventStore decorator (MIN_VALUE + 50)
        configurer.componentRegistry(cr -> cr.registerDecorator(
                EventStore.class,
                Integer.MIN_VALUE + 51,
                (config, name, delegate) -> {
                    System.out.println("=== DEBUG @ MIN_VALUE+51 (after Intercepting) ===");
                    System.out.println("  delegate type: " + delegate.getClass().getName());
                    DispatchInterceptorRegistry registry = config.getComponent(DispatchInterceptorRegistry.class);
                    List<MessageDispatchInterceptor<? super EventMessage>> interceptors = registry.eventInterceptors(config);
                    System.out.println("  Event dispatch interceptors: " + interceptors.size());
                    return delegate;
                }
        ));

        // Print decoration order constants to verify
        System.out.println("=== DECORATION ORDER CONSTANTS ===");
        System.out.println("  MultiTenantEventStore.DECORATION_ORDER = " + MultiTenantEventStore.DECORATION_ORDER);
        System.out.println("  InterceptingEventStore.DECORATION_ORDER = " + InterceptingEventStore.DECORATION_ORDER);
        System.out.println("  Integer.MIN_VALUE = " + Integer.MIN_VALUE);
        System.out.println("  Integer.MIN_VALUE + 50 = " + (Integer.MIN_VALUE + 50));

        configuration = configurer.start();
        commandGateway = configuration.getComponent(CommandGateway.class);
        queryGateway = configuration.getComponent(QueryGateway.class);

        // Debug: Check EventStore type AFTER configuration
        EventStore eventStore = configuration.getComponent(EventStore.class);
        System.out.println("=== AFTER CONFIG: EventStore ===");
        System.out.println("EventStore type: " + eventStore.getClass().getName());

        // Debug: Check dispatch interceptors AFTER configuration
        DispatchInterceptorRegistry dispatchRegistry = configuration.getComponent(DispatchInterceptorRegistry.class);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors = dispatchRegistry.eventInterceptors(configuration);
        System.out.println("=== AFTER CONFIG: Event Dispatch Interceptors ===");
        System.out.println("Number of interceptors: " + eventInterceptors.size());

        // Expected: InterceptingEventStore wrapping MultiTenantEventStore
        assertThat(eventStore).isInstanceOf(InterceptingEventStore.class)
            .withFailMessage("EventStore should be wrapped in InterceptingEventStore but was: " + eventStore.getClass().getName());
    }

    @Test
    void multiTenantConfigurationCreatesIsolatedSegments() {
        startApp();

        // Verify EventStore is wrapped with InterceptingEventStore (for dispatch interceptors)
        EventStore eventStore = configuration.getComponent(EventStore.class);
        assertThat(eventStore).isInstanceOf(InterceptingEventStore.class);

        // Verify CommandBus and QueryBus are configured
        assertThat(configuration.getComponent(CommandBus.class)).isNotNull();
        QueryBus queryBus = configuration.getComponent(QueryBus.class);
        assertThat(queryBus).isNotNull();
    }

    @Test
    void sameEntityIdInDifferentTenantsDoNotConflict() {
        startApp();

        // Use the same CourseId in both tenants - should NOT conflict
        CourseId sharedCourseId = CourseId.random();

        // Register in tenant A
        assertDoesNotThrow(() ->
                createCourseForTenant(TENANT_A, sharedCourseId, "Introduction to Multi-Tenancy A", 30)
        );

        // Register same ID in tenant B - should succeed (different tenant segment)
        assertDoesNotThrow(() ->
                createCourseForTenant(TENANT_B, sharedCourseId, "Introduction to Multi-Tenancy B", 25)
        );
    }

    @Test
    void eventsFromTenantANotVisibleInTenantB() {
        startApp();

        // Create courses for both tenants
        CourseId courseIdA = CourseId.random();
        CourseId courseIdB = CourseId.random();
        createCourseForTenant(TENANT_A, courseIdA, "Course for Tenant A", 20);
        createCourseForTenant(TENANT_B, courseIdB, "Course for Tenant B", 30);

        // Wait for both projections to process
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                !queryCoursesForTenant(TENANT_A).isEmpty() && !queryCoursesForTenant(TENANT_B).isEmpty()
        );

        // Tenant A should only see their own course
        List<CoursesStats> tenantACourses = queryCoursesForTenant(TENANT_A);
        assertThat(tenantACourses).hasSize(1);
        assertThat(tenantACourses.get(0).courseId()).isEqualTo(courseIdA);
        assertThat(tenantACourses.get(0).name()).isEqualTo("Course for Tenant A");

        // Tenant B should only see their own course - not Tenant A's
        List<CoursesStats> tenantBCourses = queryCoursesForTenant(TENANT_B);
        assertThat(tenantBCourses).hasSize(1);
        assertThat(tenantBCourses.get(0).courseId()).isEqualTo(courseIdB);
        assertThat(tenantBCourses.get(0).name()).isEqualTo("Course for Tenant B");
    }

    @Test
    void commandWithoutTenantMetadataFails() {
        startApp();

        CourseId courseId = CourseId.random();
        CreateCourse command = new CreateCourse(courseId, "Test Course without Tenant", 10);

        // Send without tenant metadata - should fail
        assertThatThrownBy(() -> commandGateway.sendAndWait(command))
                .isInstanceOf(NoSuchTenantException.class);
    }

    @Test
    void dynamicTenantRegistrationWorks() {
        startApp();

        TenantDescriptor dynamicTenant = TenantDescriptor.tenantWithId("dynamic-tenant");

        // Initially, operations to dynamic tenant should fail
        CourseId courseId1 = CourseId.random();
        assertThatThrownBy(() ->
                createCourseForTenant(dynamicTenant, courseId1, "Test Course for Dynamic", 15)
        ).hasCauseInstanceOf(NoSuchTenantException.class);

        // Add the tenant dynamically
        tenantProvider.addTenant(dynamicTenant);

        // Now operations should succeed
        CourseId courseId2 = CourseId.random();
        assertDoesNotThrow(() ->
                createCourseForTenant(dynamicTenant, courseId2, "Dynamic Tenant Course", 20)
        );

        // Verify the course was stored
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                !queryCoursesForTenant(dynamicTenant).isEmpty()
        );
        List<CoursesStats> dynamicTenantCourses = queryCoursesForTenant(dynamicTenant);
        assertThat(dynamicTenantCourses).hasSize(1);
    }

    // --- Tests for CommandDispatcher tenant propagation ---

    private Map<String, NotificationService> notificationServices;

    private void startAppWithNotifications() {
        tenantProvider = new SimpleTenantProvider(List.of(TENANT_A, TENANT_B));
        notificationServices = new ConcurrentHashMap<>();

        EventSourcingConfigurer configurer = EventSourcingConfigurer.create();

        MultiTenancyConfigurer multiTenancyConfigurer = MultiTenancyConfigurer.enhance(configurer)
                .registerTenantProvider(config -> tenantProvider)
                .registerTargetTenantResolver(config -> new MetadataBasedTenantResolver());

        // Domain modules
        configurer = CreateCourseConfiguration.configure(configurer);
        configurer = CourseStatsConfiguration.configure(configurer);
        configurer = CourseNotificationConfiguration.configure(configurer);

        // Tenant-scoped components
        multiTenancyConfigurer.tenantComponent(CourseStatsRepository.class, tenant -> new InMemoryCourseStatsRepository());
        multiTenancyConfigurer.tenantComponent(NotificationService.class, tenant -> {
            var service = new NotificationService(tenant.tenantId());
            notificationServices.put(tenant.tenantId(), service);
            return service;
        });

        // Correlation provider for tenant propagation
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

    @Test
    void commandDispatcherPropagatesTenantContext() {
        startAppWithNotifications();

        // Create courses for both tenants
        CourseId courseIdA = CourseId.random();
        CourseId courseIdB = CourseId.random();
        createCourseForTenant(TENANT_A, courseIdA, "Course A", 20);
        createCourseForTenant(TENANT_B, courseIdB, "Course B", 30);

        // Wait for notifications to be sent via the automation
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var serviceA = notificationServices.get(TENANT_A.tenantId());
            var serviceB = notificationServices.get(TENANT_B.tenantId());
            return serviceA != null && !serviceA.getSentNotifications().isEmpty()
                    && serviceB != null && !serviceB.getSentNotifications().isEmpty();
        });

        // Get the tenant-specific NotificationService instances
        NotificationService tenantAService = notificationServices.get(TENANT_A.tenantId());
        NotificationService tenantBService = notificationServices.get(TENANT_B.tenantId());

        // Verify each service was constructed with the correct tenant ID
        assertThat(tenantAService.getTenantId()).isEqualTo(TENANT_A.tenantId());
        assertThat(tenantBService.getTenantId()).isEqualTo(TENANT_B.tenantId());

        // Verify tenant A's service received ONLY tenant A's notification
        assertThat(tenantAService.getSentNotifications())
                .as("Tenant A's service should receive exactly 1 notification")
                .hasSize(1);
        assertThat(tenantAService.getSentNotifications().get(0).message())
                .as("Tenant A's notification should be about Course A")
                .contains("Course A");
        assertThat(tenantAService.getSentNotifications().get(0).message())
                .as("Tenant A's service should NOT have Course B's notification")
                .doesNotContain("Course B");

        // Verify tenant B's service received ONLY tenant B's notification
        assertThat(tenantBService.getSentNotifications())
                .as("Tenant B's service should receive exactly 1 notification")
                .hasSize(1);
        assertThat(tenantBService.getSentNotifications().get(0).message())
                .as("Tenant B's notification should be about Course B")
                .contains("Course B");
        assertThat(tenantBService.getSentNotifications().get(0).message())
                .as("Tenant B's service should NOT have Course A's notification")
                .doesNotContain("Course A");
    }
}
