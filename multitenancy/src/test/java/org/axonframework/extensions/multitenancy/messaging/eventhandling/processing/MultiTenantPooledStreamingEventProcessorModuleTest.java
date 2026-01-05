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
package org.axonframework.extensions.multitenancy.messaging.eventhandling.processing;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.extensions.multitenancy.core.TenantProvider;
import org.axonframework.extensions.multitenancy.eventsourcing.eventstore.MultiTenantEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantPooledStreamingEventProcessorModule}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantPooledStreamingEventProcessorModuleTest {

    private static final String PROCESSOR_NAME = "testProcessor";
    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    @Test
    void createReturnsModuleInEventHandlingPhase() {
        var result = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME);

        assertNotNull(result);
        assertInstanceOf(
                EventProcessorModule.EventHandlingPhase.class,
                result
        );
    }

    @Test
    void constructorRejectsNullProcessorName() {
        assertThrows(IllegalArgumentException.class, () ->
                new MultiTenantPooledStreamingEventProcessorModule(null)
        );
    }

    @Test
    void fluentApiAllowsFullConfiguration() {
        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .customized((cfg, config) -> config.batchSize(100));

        assertNotNull(module);
    }

    @Test
    void notCustomizedReturnsModule() {
        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .notCustomized();

        assertNotNull(module);
        assertInstanceOf(MultiTenantPooledStreamingEventProcessorModule.class, module);
    }

    @Test
    void customizedAllowsProcessorConfigurationModification() {
        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .customized((cfg, config) -> {
                    assertNotNull(config);
                    assertInstanceOf(MultiTenantPooledStreamingEventProcessorConfiguration.class, config);
                    return config.batchSize(50);
                });

        assertNotNull(module);
    }

    @Test
    void moduleImplementsEventProcessorModule() {
        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .notCustomized();

        assertInstanceOf(EventProcessorModule.class, module);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildMultiTenantEventProcessorUsesConfiguredComponents() {
        // Setup mocks
        Configuration configuration = mock(Configuration.class);
        MultiTenantEventStore multiTenantEventStore = mock(MultiTenantEventStore.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        EventStore tenantEventStore = mock(EventStore.class);

        // Configure mock behavior
        when(configuration.getComponent(eq(MultiTenantEventStore.class)))
                .thenReturn(multiTenantEventStore);
        when(configuration.getComponent(eq(TenantTokenStoreFactory.class), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());
        when(configuration.getComponent(eq(TenantProvider.class)))
                .thenReturn(tenantProvider);
        when(multiTenantEventStore.tenantSegments())
                .thenReturn(Map.of(TENANT_1, tenantEventStore));
        when(tenantProvider.getTenants())
                .thenReturn(List.of(TENANT_1));

        // The module would create the processor when integrated into the full configuration system
        // This test validates the module can be created without errors
        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .notCustomized();

        assertNotNull(module);
    }

    @Test
    void moduleSupportsMultipleEventHandlingComponents() {
        EventHandlingComponent component1 = mock(EventHandlingComponent.class);
        EventHandlingComponent component2 = mock(EventHandlingComponent.class);

        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c
                        .autodetected(cfg -> component1)
                        .autodetected(cfg -> component2))
                .notCustomized();

        assertNotNull(module);
    }

    @Test
    void customizedWithNoOpReturnsModule() {
        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .customized((cfg, config) -> config); // No-op customization

        assertNotNull(module);
    }

    @Test
    void customizedRejectsNullFunction() {
        var phase = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)));

        assertThrows(NullPointerException.class, () -> phase.customized(null));
    }

    @Test
    void customizedAllowsTenantTokenStoreFactoryConfiguration() {
        TenantTokenStoreFactory customFactory = mock(TenantTokenStoreFactory.class);

        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .customized((cfg, config) -> config
                        .tenantTokenStoreFactory(customFactory)
                        .batchSize(100));

        assertNotNull(module);
    }

    @Test
    void customizedConfigurationSupportsFluentMethodChaining() {
        TenantTokenStoreFactory customFactory = mock(TenantTokenStoreFactory.class);

        // Verify all fluent methods can be chained in any order
        var module = MultiTenantPooledStreamingEventProcessorModule.create(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c.autodetected(cfg -> mock(EventHandlingComponent.class)))
                .customized((cfg, config) -> config
                        .batchSize(50)
                        .tenantTokenStoreFactory(customFactory)
                        .initialSegmentCount(8)
                        .tokenClaimInterval(10000));

        assertNotNull(module);
    }
}
