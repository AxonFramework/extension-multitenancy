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
package org.axonframework.extension.multitenancy.core.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.extension.multitenancy.core.MultiTenantAwareComponent;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.eventsourcing.eventstore.MultiTenantEventStore;
import org.axonframework.extension.multitenancy.eventsourcing.eventstore.TenantEventSegmentFactory;
import org.axonframework.extension.multitenancy.eventsourcing.eventstore.TenantEventStoreProvider;
import org.axonframework.extension.multitenancy.messaging.commandhandling.MultiTenantCommandBus;
import org.axonframework.extension.multitenancy.messaging.commandhandling.TenantAwareCommandBus;
import org.axonframework.extension.multitenancy.messaging.commandhandling.TenantCommandSegmentFactory;
import org.axonframework.extension.multitenancy.messaging.queryhandling.MultiTenantQueryBus;
import org.axonframework.extension.multitenancy.messaging.queryhandling.TenantQuerySegmentFactory;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.InterceptingEventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.interception.DispatchInterceptorRegistry;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.SimpleCommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycleHandlerRegistrar;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.SimpleQueryBus;

import java.util.Collections;
import java.util.List;

/**
 * {@link ConfigurationEnhancer} that provides configuration for multi-tenancy components.
 * <p>
 * This enhancer provides default segment factories for embedded (non-distributed) deployments
 * and registers decorators that replace standard infrastructure components (CommandBus,
 * QueryBus, EventStore) with their multi-tenant equivalents when a {@link TargetTenantResolver}
 * is configured.
 * <p>
 * <b>Default Segment Factories:</b> For embedded deployments without Axon Server, this
 * enhancer provides default implementations:
 * <ul>
 *   <li>{@link TenantCommandSegmentFactory} - creates {@link SimpleCommandBus} per tenant</li>
 *   <li>{@link TenantQuerySegmentFactory} - creates {@link SimpleQueryBus} per tenant</li>
 *   <li>{@link TenantEventSegmentFactory} - creates in-memory {@link EventStore} per tenant</li>
 * </ul>
 * These defaults can be overridden by registering custom implementations.
 * <p>
 * <b>Decoration Order:</b> Multi-tenant decorators run BEFORE intercepting decorators
 * (e.g., {@code InterceptingCommandBus}). This means the decoration chain is:
 * <pre>
 *     User → InterceptingCommandBus → MultiTenantCommandBus → TenantSegments
 * </pre>
 * This follows the standard Axon Framework pattern where interceptors wrap the outer bus,
 * and the multi-tenant bus handles routing to tenant-specific segments.
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public class MultiTenancyConfigurationDefaults implements ConfigurationEnhancer {

    /**
     * The order of {@code this} enhancer compared to others.
     * <p>
     * Using {@code Integer.MAX_VALUE - 1} ensures multi-tenancy configuration runs after
     * most other enhancers but before the final defaults.
     */
    public static final int ENHANCER_ORDER = Integer.MAX_VALUE - 1;

    // Holds the MultiTenantEventStore instance created during decoration
    // This is needed to register TenantEventStoreProvider for components that need tenant segments
    private volatile MultiTenantEventStore multiTenantEventStoreInstance;

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        // Register default segment factories for embedded mode (can be overridden)
        componentRegistry.registerIfNotPresent(
                TenantCommandSegmentFactory.class,
                config -> tenant -> defaultCommandBusSegment(config, tenant)
        );
        componentRegistry.registerIfNotPresent(
                TenantQuerySegmentFactory.class,
                config -> tenant -> defaultQueryBusSegment(config, tenant)
        );
        componentRegistry.registerIfNotPresent(
                TenantEventSegmentFactory.class,
                config -> tenant -> defaultEventStoreSegment(config, tenant)
        );

        // Register decorator to replace CommandBus with MultiTenantCommandBus
        // Uses decoration order from MultiTenantCommandBus.DECORATION_ORDER
        componentRegistry.registerDecorator(
                CommandBus.class,
                MultiTenantCommandBus.DECORATION_ORDER,
                (config, name, delegate) -> createMultiTenantCommandBus(config, delegate)
        );

        // Register decorator to replace QueryBus with MultiTenantQueryBus
        // Uses decoration order from MultiTenantQueryBus.DECORATION_ORDER
        componentRegistry.registerDecorator(
                QueryBus.class,
                MultiTenantQueryBus.DECORATION_ORDER,
                (config, name, delegate) -> createMultiTenantQueryBus(config, delegate)
        );

        // Register decorator to replace EventStore with MultiTenantEventStore
        // Uses decoration order from MultiTenantEventStore.DECORATION_ORDER
        componentRegistry.registerDecorator(
                EventStore.class,
                MultiTenantEventStore.DECORATION_ORDER,
                (config, name, delegate) -> createMultiTenantEventStore(config, delegate)
        );

        // Register TenantEventStoreProvider to allow components to access tenant segments
        // without depending on the concrete MultiTenantEventStore type (which may be decorated)
        componentRegistry.registerComponent(
                TenantEventStoreProvider.class,
                config -> {
                    // Ensure EventStore is resolved first, which triggers the decorator chain
                    // and populates multiTenantEventStoreInstance
                    config.getComponent(EventStore.class);
                    return multiTenantEventStoreInstance;
                }
        );
    }

    @SuppressWarnings("unchecked")
    private CommandBus createMultiTenantCommandBus(Configuration config, CommandBus delegate) {
        // Only wrap if we have both a segment factory and resolver configured
        if (!config.hasComponent(TenantCommandSegmentFactory.class) ||
            !config.hasComponent(TargetTenantResolver.class)) {
            return delegate;
        }

        TenantCommandSegmentFactory segmentFactory = config.getComponent(TenantCommandSegmentFactory.class);
        TargetTenantResolver<Message> resolver = config.getComponent(TargetTenantResolver.class);

        MultiTenantCommandBus multiTenantBus = MultiTenantCommandBus.builder()
                .tenantSegmentFactory(segmentFactory)
                .targetTenantResolver(resolver)
                .build();

        registerTenantsIfProviderAvailable(config, multiTenantBus);
        return multiTenantBus;
    }

    @SuppressWarnings("unchecked")
    private QueryBus createMultiTenantQueryBus(Configuration config, QueryBus delegate) {
        // Only wrap if we have both a segment factory and resolver configured
        if (!config.hasComponent(TenantQuerySegmentFactory.class) ||
            !config.hasComponent(TargetTenantResolver.class)) {
            return delegate;
        }

        TenantQuerySegmentFactory segmentFactory = config.getComponent(TenantQuerySegmentFactory.class);
        TargetTenantResolver<Message> resolver = config.getComponent(TargetTenantResolver.class);

        MultiTenantQueryBus multiTenantBus = MultiTenantQueryBus.builder()
                .tenantSegmentFactory(segmentFactory)
                .targetTenantResolver(resolver)
                .build();

        registerTenantsIfProviderAvailable(config, multiTenantBus);
        return multiTenantBus;
    }

    @SuppressWarnings("unchecked")
    private EventStore createMultiTenantEventStore(Configuration config, EventStore delegate) {
        // Only wrap if we have both a segment factory and resolver configured
        if (!config.hasComponent(TenantEventSegmentFactory.class) ||
            !config.hasComponent(TargetTenantResolver.class)) {
            return delegate;
        }

        TenantEventSegmentFactory segmentFactory = config.getComponent(TenantEventSegmentFactory.class);
        TargetTenantResolver<Message> resolver = config.getComponent(TargetTenantResolver.class);

        MultiTenantEventStore multiTenantStore = MultiTenantEventStore.builder()
                .tenantSegmentFactory(segmentFactory)
                .targetTenantResolver(resolver)
                .build();

        // Store the instance for direct component lookup
        this.multiTenantEventStoreInstance = multiTenantStore;

        registerTenantsIfProviderAvailable(config, multiTenantStore);
        return multiTenantStore;
    }

    private void registerTenantsIfProviderAvailable(Configuration config, MultiTenantAwareComponent component) {
        if (config.hasComponent(TenantProvider.class)) {
            TenantProvider tenantProvider = config.getComponent(TenantProvider.class);
            tenantProvider.subscribe(component);
            tenantProvider.getTenants().forEach(component::registerTenant);
        }
    }

    /**
     * Creates a default {@link CommandBus} for the given tenant.
     * <p>
     * Returns a {@link TenantAwareCommandBus} wrapping a {@link SimpleCommandBus}.
     * The wrapper ensures that command messages are added to the processing context,
     * which is required for multi-tenant event store operations.
     * <p>
     * Uses the configured {@link UnitOfWorkFactory} and {@link TransactionManager} from
     * the main configuration to ensure tenant command buses have the same interceptors
     * and lifecycle handlers as the main application.
     *
     * @param config the configuration to use for component resolution
     * @param tenant the tenant for which to create the command bus
     * @return a new tenant-aware {@link CommandBus} for the tenant
     */
    private CommandBus defaultCommandBusSegment(Configuration config, TenantDescriptor tenant) {
        SimpleCommandBus simpleCommandBus = new SimpleCommandBus(
                config.getComponent(UnitOfWorkFactory.class),
                config.getOptionalComponent(TransactionManager.class)
                      .map(tm -> (ProcessingLifecycleHandlerRegistrar) tm)
                      .map(List::of)
                      .orElse(Collections.emptyList())
        );
        return new TenantAwareCommandBus(simpleCommandBus);
    }

    /**
     * Creates a default {@link QueryBus} for the given tenant.
     * <p>
     * Returns a new {@link SimpleQueryBus} instance using the configured {@link UnitOfWorkFactory}.
     * This ensures tenant query buses have the same interceptors and lifecycle handlers
     * as the main application.
     *
     * @param config the configuration to use for component resolution
     * @param tenant the tenant for which to create the query bus
     * @return a new {@link SimpleQueryBus} for the tenant
     */
    private QueryBus defaultQueryBusSegment(Configuration config, TenantDescriptor tenant) {
        return new SimpleQueryBus(config.getComponent(UnitOfWorkFactory.class));
    }

    /**
     * Creates a default {@link EventStore} for the given tenant.
     * <p>
     * Returns a new {@link StorageEngineBackedEventStore} with an {@link InMemoryEventStorageEngine},
     * wrapped in an {@link InterceptingEventStore} to apply dispatch interceptors (including
     * correlation data propagation).
     * <p>
     * This is suitable for testing and development, but not for production use as events
     * are not persisted.
     *
     * @param config the configuration to use for component resolution
     * @param tenant the tenant for which to create the event store
     * @return a new in-memory {@link EventStore} for the tenant with interception
     */
    private EventStore defaultEventStoreSegment(Configuration config, TenantDescriptor tenant) {
        EventStore rawEventStore = new StorageEngineBackedEventStore(
                new InMemoryEventStorageEngine(),
                new SimpleEventBus(),
                new AnnotationBasedTagResolver()
        );

        // Wrap with interceptors to ensure correlation data (including tenant) is applied
        List<MessageDispatchInterceptor<? super EventMessage>> dispatchInterceptors =
                config.getComponent(DispatchInterceptorRegistry.class).eventInterceptors(config);

        return dispatchInterceptors.isEmpty()
                ? rawEventStore
                : new InterceptingEventStore(rawEventStore, dispatchInterceptors);
    }
}
