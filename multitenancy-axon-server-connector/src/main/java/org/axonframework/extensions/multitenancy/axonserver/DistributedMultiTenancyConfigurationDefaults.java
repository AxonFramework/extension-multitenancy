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
package org.axonframework.extensions.multitenancy.axonserver;

import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.configuration.SearchScope;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.extensions.multitenancy.core.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.core.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.core.TenantProvider;
import org.axonframework.extensions.multitenancy.core.configuration.MultiTenancyConfigurationDefaults;
import org.axonframework.extensions.multitenancy.eventsourcing.eventstore.TenantEventSegmentFactory;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;

/**
 * A {@link ConfigurationEnhancer} that provides default multi-tenancy components for distributed
 * deployments using Axon Server. This enhancer is auto-discovered via SPI when the module is on
 * the classpath.
 * <p>
 * This enhancer implements the <b>wrapping pattern</b>: rather than creating per-tenant
 * {@code DistributedCommandBus} and {@code DistributedQueryBus} instances, it provides multi-tenant
 * connectors that wrap the standard framework infrastructure. This ensures all framework decorators
 * (such as {@code PayloadConvertingCommandBusConnector}) are automatically applied.
 * <p>
 * When activated, this enhancer registers:
 * <ul>
 *     <li>{@link AxonServerTenantProvider} as the {@link TenantProvider} - discovers tenants from Axon Server contexts</li>
 *     <li>{@link MultiTenantAxonServerCommandBusConnector} as the {@link CommandBusConnector} - routes commands to tenant-specific connectors</li>
 *     <li>{@link MultiTenantAxonServerQueryBusConnector} as the {@link QueryBusConnector} - routes queries to tenant-specific connectors</li>
 *     <li>{@link AxonServerTenantEventSegmentFactory} as the {@link TenantEventSegmentFactory} - creates per-tenant event stores</li>
 * </ul>
 * <p>
 * The multi-tenant connectors are subscribed to the {@link TenantProvider} during the
 * {@link Phase#INSTRUCTION_COMPONENTS} phase, enabling them to receive tenant lifecycle events
 * and create/destroy tenant-specific connectors dynamically.
 * <p>
 * The enhancer only registers components if:
 * <ul>
 *     <li>An {@link AxonServerConnectionManager} is available in the configuration</li>
 *     <li>A {@link org.axonframework.extensions.multitenancy.core.TargetTenantResolver TargetTenantResolver} is available</li>
 *     <li>No other implementation has been explicitly registered for that component type</li>
 * </ul>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see AxonServerTenantProvider
 * @see MultiTenantAxonServerCommandBusConnector
 * @see MultiTenantAxonServerQueryBusConnector
 * @see AxonServerTenantEventSegmentFactory
 */
public class DistributedMultiTenancyConfigurationDefaults implements ConfigurationEnhancer {

    /**
     * The order of this enhancer. Runs just before {@code MultiTenancyConfigurationDefaults}
     * to register Axon Server-specific components that override the embedded defaults.
     * <p>
     * Both enhancers use {@code registerIfNotPresent()} - whichever runs first wins.
     * This ensures Axon Server implementations take precedence when on the classpath.
     */
    public static final int ENHANCER_ORDER = MultiTenancyConfigurationDefaults.ENHANCER_ORDER - 5;

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(tenantProviderDefinition(), SearchScope.ALL);
        registry.registerIfNotPresent(commandBusConnectorDefinition(), SearchScope.ALL);
        registry.registerIfNotPresent(queryBusConnectorDefinition(), SearchScope.ALL);
        registry.registerIfNotPresent(eventSegmentFactoryDefinition(), SearchScope.ALL);
    }

    private static ComponentDefinition<TenantProvider> tenantProviderDefinition() {
        return ComponentDefinition.ofType(TenantProvider.class)
                                  .withBuilder(config -> {
                                      // Only build if AxonServerConnectionManager is available
                                      return config.getOptionalComponent(AxonServerConnectionManager.class)
                                                   .map(connectionManager -> AxonServerTenantProvider.builder()
                                                           .axonServerConnectionManager(connectionManager)
                                                           .tenantConnectPredicate(
                                                                   config.getComponent(
                                                                           TenantConnectPredicate.class,
                                                                           () -> tenant -> true
                                                                   )
                                                           )
                                                           .build())
                                                   .orElse(null);
                                  })
                                  .onStart(Phase.INSTRUCTION_COMPONENTS + 10,
                                           provider -> ((AxonServerTenantProvider) provider).start())
                                  .onShutdown(Phase.INSTRUCTION_COMPONENTS + 10,
                                              provider -> ((AxonServerTenantProvider) provider).shutdown());
    }

    private static ComponentDefinition<CommandBusConnector> commandBusConnectorDefinition() {
        return ComponentDefinition.ofType(CommandBusConnector.class)
                                  .withBuilder(MultiTenantAxonServerCommandBusConnector::createFrom)
                                  .onStart(Phase.INSTRUCTION_COMPONENTS,
                                           (Configuration config, CommandBusConnector connector) ->
                                                   subscribeToTenantProvider(config, connector))
                                  .onShutdown(Phase.OUTBOUND_COMMAND_CONNECTORS,
                                              connector -> ((MultiTenantAxonServerCommandBusConnector) connector).shutdown());
    }

    private static ComponentDefinition<QueryBusConnector> queryBusConnectorDefinition() {
        return ComponentDefinition.ofType(QueryBusConnector.class)
                                  .withBuilder(MultiTenantAxonServerQueryBusConnector::createFrom)
                                  .onStart(Phase.INSTRUCTION_COMPONENTS,
                                           (Configuration config, QueryBusConnector connector) ->
                                                   subscribeToTenantProvider(config, connector))
                                  .onShutdown(Phase.OUTBOUND_QUERY_CONNECTORS,
                                              connector -> ((MultiTenantAxonServerQueryBusConnector) connector).shutdown());
    }

    private static void subscribeToTenantProvider(Configuration config, Object connector) {
        if (connector instanceof MultiTenantAwareComponent multiTenantConnector) {
            config.getOptionalComponent(TenantProvider.class)
                  .ifPresent(provider -> provider.subscribe(multiTenantConnector));
        }
    }

    private static ComponentDefinition<TenantEventSegmentFactory> eventSegmentFactoryDefinition() {
        return ComponentDefinition.ofType(TenantEventSegmentFactory.class)
                                  .withBuilder(AxonServerTenantEventSegmentFactory::createFrom);
    }

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }
}
