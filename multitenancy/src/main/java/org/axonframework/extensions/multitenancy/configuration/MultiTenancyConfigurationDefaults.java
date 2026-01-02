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
package org.axonframework.extensions.multitenancy.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.eventsourcing.eventstore.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.eventsourcing.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.commandhandling.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.messaging.commandhandling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.queryhandling.MultiTenantQueryBus;
import org.axonframework.extensions.multitenancy.messaging.queryhandling.TenantQuerySegmentFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.queryhandling.QueryBus;

/**
 * {@link ConfigurationEnhancer} that provides configuration for multi-tenancy components.
 * <p>
 * This enhancer registers decorators that replace standard infrastructure components
 * (CommandBus, QueryBus, EventStore) with their multi-tenant equivalents when the
 * required components are available:
 * <ul>
 *   <li>A {@link TenantCommandSegmentFactory} for multi-tenant command bus</li>
 *   <li>A {@link TenantQuerySegmentFactory} for multi-tenant query bus</li>
 *   <li>A {@link TenantEventSegmentFactory} for multi-tenant event store</li>
 *   <li>A {@link TargetTenantResolver} for resolving tenants from messages</li>
 * </ul>
 * <p>
 * Unlike other configuration defaults, this enhancer does <b>not</b> provide default
 * implementations. Users must explicitly configure the tenant segment factories and
 * resolver, typically via {@link MultiTenancyConfigurer} or Spring Boot autoconfiguration.
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

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
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
}
