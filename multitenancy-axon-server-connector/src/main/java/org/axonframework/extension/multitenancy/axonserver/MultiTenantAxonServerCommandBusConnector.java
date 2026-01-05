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
package org.axonframework.extension.multitenancy.axonserver;

import io.axoniq.axonserver.connector.AxonServerConnection;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.command.AxonServerCommandBusConnector;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.extension.multitenancy.core.MultiTenantAwareComponent;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A multi-tenant implementation of {@link CommandBusConnector} that routes commands to tenant-specific
 * {@link AxonServerCommandBusConnector} instances.
 * <p>
 * This connector implements the wrapping pattern: rather than creating per-tenant {@code DistributedCommandBus}
 * instances, it wraps the standard framework infrastructure by providing a single connector that internally
 * routes to tenant-specific Axon Server connections. This ensures all framework decorators (such as
 * {@code PayloadConvertingCommandBusConnector}) are applied automatically.
 * <p>
 * The connector manages tenant-specific connectors in response to tenant lifecycle events via the
 * {@link MultiTenantAwareComponent} interface. When a tenant is registered, a new
 * {@link AxonServerCommandBusConnector} is created for that tenant's context. Existing command subscriptions
 * are automatically replayed to the new connector.
 * <p>
 * Usage:
 * <pre>{@code
 * MultiTenantAxonServerCommandBusConnector connector = MultiTenantAxonServerCommandBusConnector.builder()
 *     .connectionManager(connectionManager)
 *     .axonServerConfiguration(axonServerConfig)
 *     .targetTenantResolver(tenantResolver)
 *     .build();
 *
 * // Subscribe to tenant provider to receive tenant lifecycle events
 * tenantProvider.subscribe(connector);
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see CommandBusConnector
 * @see MultiTenantAwareComponent
 * @see AxonServerCommandBusConnector
 */
public class MultiTenantAxonServerCommandBusConnector implements CommandBusConnector, MultiTenantAwareComponent {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantAxonServerCommandBusConnector.class);

    private final AxonServerConnectionManager connectionManager;
    private final AxonServerConfiguration axonServerConfiguration;
    private final TargetTenantResolver<Message> targetTenantResolver;

    private final Map<TenantDescriptor, AxonServerCommandBusConnector> connectors = new ConcurrentHashMap<>();
    private final Map<QualifiedName, Integer> subscriptions = new ConcurrentHashMap<>();

    private volatile Handler incomingHandler;

    /**
     * Instantiate a {@link MultiTenantAxonServerCommandBusConnector} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantAxonServerCommandBusConnector}.
     */
    protected MultiTenantAxonServerCommandBusConnector(Builder builder) {
        builder.validate();
        this.connectionManager = builder.connectionManager;
        this.axonServerConfiguration = builder.axonServerConfiguration;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Creates a {@link MultiTenantAxonServerCommandBusConnector} from the given {@link Configuration}.
     *
     * @param config The Axon {@link Configuration} to obtain components from.
     * @return A new connector instance, or {@code null} if required components are not available.
     */
    @Nullable
    public static MultiTenantAxonServerCommandBusConnector createFrom(@Nonnull Configuration config) {
        return config.getOptionalComponent(AxonServerConnectionManager.class)
                     .flatMap(connectionManager -> config.getOptionalComponent(TargetTenantResolver.class)
                             .map(resolver -> MultiTenantAxonServerCommandBusConnector.builder()
                                     .connectionManager(connectionManager)
                                     .axonServerConfiguration(config.getComponent(AxonServerConfiguration.class))
                                     .targetTenantResolver(resolver)
                                     .build()))
                     .orElse(null);
    }

    /**
     * Instantiate a builder to construct a {@link MultiTenantAxonServerCommandBusConnector}.
     *
     * @return A Builder to create a {@link MultiTenantAxonServerCommandBusConnector}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        TenantDescriptor tenant = targetTenantResolver.resolveTenant(command, connectors.keySet());
        AxonServerCommandBusConnector connector = connectors.get(tenant);
        if (connector == null) {
            return CompletableFuture.failedFuture(NoSuchTenantException.forTenantId(tenant.tenantId()));
        }
        return connector.dispatch(command, processingContext);
    }

    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QualifiedName commandName, int loadFactor) {
        logger.debug("Subscribing to command [{}] with load factor [{}] across all tenants", commandName, loadFactor);
        subscriptions.put(commandName, loadFactor);

        if (connectors.isEmpty()) {
            // No tenants registered yet; subscription will be replayed when tenants join
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(
                connectors.values().stream()
                          .map(connector -> connector.subscribe(commandName, loadFactor))
                          .toArray(CompletableFuture[]::new)
        );
    }

    @Override
    public boolean unsubscribe(@Nonnull QualifiedName commandName) {
        subscriptions.remove(commandName);

        boolean allUnsubscribed = true;
        for (AxonServerCommandBusConnector connector : connectors.values()) {
            if (!connector.unsubscribe(commandName)) {
                allUnsubscribed = false;
            }
        }
        return allUnsubscribed;
    }

    @Override
    public void onIncomingCommand(@Nonnull Handler handler) {
        this.incomingHandler = handler;
        connectors.values().forEach(connector -> connector.onIncomingCommand(handler));
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        logger.debug("Registering tenant [{}] for command routing", tenantDescriptor.tenantId());

        AxonServerConnection connection = connectionManager.getConnection(tenantDescriptor.tenantId());
        AxonServerCommandBusConnector connector = new AxonServerCommandBusConnector(
                connection,
                axonServerConfiguration
        );
        connectors.put(tenantDescriptor, connector);

        // Set incoming handler if already configured
        if (incomingHandler != null) {
            connector.onIncomingCommand(incomingHandler);
        }

        connector.start();

        return () -> {
            logger.debug("Unregistering tenant [{}] from command routing", tenantDescriptor.tenantId());
            AxonServerCommandBusConnector removed = connectors.remove(tenantDescriptor);
            if (removed != null) {
                removed.disconnect()
                       .thenCompose(v -> removed.shutdownDispatching())
                       .join();
            }
            return removed != null;
        };
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        Registration registration = registerTenant(tenantDescriptor);

        // Replay existing subscriptions to the new tenant connector
        AxonServerCommandBusConnector connector = connectors.get(tenantDescriptor);
        if (connector != null) {
            subscriptions.forEach((commandName, loadFactor) -> {
                logger.debug("Replaying subscription [{}] to tenant [{}]",
                             commandName, tenantDescriptor.tenantId());
                connector.subscribe(commandName, loadFactor);
            });
        }

        return registration;
    }

    /**
     * Shuts down all tenant connectors.
     *
     * @return A {@link CompletableFuture} that completes when all connectors have been shut down.
     */
    public CompletableFuture<Void> shutdown() {
        logger.debug("Shutting down {} command bus connectors", connectors.size());
        return CompletableFuture.allOf(
                connectors.values().stream()
                          .map(connector -> connector.disconnect()
                                                     .thenCompose(v -> connector.shutdownDispatching()))
                          .toArray(CompletableFuture[]::new)
        );
    }

    /**
     * Returns the tenant-specific connectors managed by this multi-tenant connector.
     * Primarily for testing purposes.
     *
     * @return An unmodifiable view of the tenant-to-connector mapping.
     */
    Map<TenantDescriptor, AxonServerCommandBusConnector> connectors() {
        return Collections.unmodifiableMap(connectors);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("connectionManager", connectionManager);
        descriptor.describeProperty("tenants", connectors.keySet());
        descriptor.describeProperty("subscriptions", subscriptions.keySet());
    }

    /**
     * Builder class to instantiate a {@link MultiTenantAxonServerCommandBusConnector}.
     * <p>
     * The {@link AxonServerConnectionManager}, {@link AxonServerConfiguration}, and
     * {@link TargetTenantResolver} are <b>hard requirements</b> and must be provided.
     */
    public static class Builder {

        private AxonServerConnectionManager connectionManager;
        private AxonServerConfiguration axonServerConfiguration;
        private TargetTenantResolver<Message> targetTenantResolver;

        /**
         * Sets the {@link AxonServerConnectionManager} used to obtain tenant-specific connections.
         *
         * @param connectionManager The connection manager providing Axon Server connections per tenant context.
         * @return The current builder instance, for fluent interfacing.
         */
        public Builder connectionManager(@Nonnull AxonServerConnectionManager connectionManager) {
            assertNonNull(connectionManager, "AxonServerConnectionManager may not be null");
            this.connectionManager = connectionManager;
            return this;
        }

        /**
         * Sets the {@link AxonServerConfiguration} for Axon Server settings.
         *
         * @param axonServerConfiguration The Axon Server configuration.
         * @return The current builder instance, for fluent interfacing.
         */
        public Builder axonServerConfiguration(@Nonnull AxonServerConfiguration axonServerConfiguration) {
            assertNonNull(axonServerConfiguration, "AxonServerConfiguration may not be null");
            this.axonServerConfiguration = axonServerConfiguration;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve which tenant a command belongs to.
         *
         * @param targetTenantResolver The resolver that determines the target tenant for each command.
         * @return The current builder instance, for fluent interfacing.
         */
        @SuppressWarnings("unchecked")
        public Builder targetTenantResolver(@Nonnull TargetTenantResolver<? extends Message> targetTenantResolver) {
            assertNonNull(targetTenantResolver, "TargetTenantResolver may not be null");
            this.targetTenantResolver = (TargetTenantResolver<Message>) targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantAxonServerCommandBusConnector} as specified through this Builder.
         *
         * @return A {@link MultiTenantAxonServerCommandBusConnector} as specified through this Builder.
         */
        public MultiTenantAxonServerCommandBusConnector build() {
            return new MultiTenantAxonServerCommandBusConnector(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(connectionManager,
                          "The AxonServerConnectionManager is a hard requirement and should be provided");
            assertNonNull(axonServerConfiguration,
                          "The AxonServerConfiguration is a hard requirement and should be provided");
            assertNonNull(targetTenantResolver,
                          "The TargetTenantResolver is a hard requirement and should be provided");
        }
    }
}
