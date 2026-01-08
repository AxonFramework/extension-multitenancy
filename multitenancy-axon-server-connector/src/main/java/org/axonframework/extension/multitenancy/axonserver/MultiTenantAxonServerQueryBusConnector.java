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
import org.axonframework.axonserver.connector.query.AxonServerQueryBusConnector;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.extension.multitenancy.core.MultiTenantAwareComponent;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A multi-tenant implementation of {@link QueryBusConnector} that routes queries to tenant-specific
 * {@link AxonServerQueryBusConnector} instances.
 * <p>
 * This connector implements the wrapping pattern: rather than creating per-tenant {@code DistributedQueryBus}
 * instances, it wraps the standard framework infrastructure by providing a single connector that internally
 * routes to tenant-specific Axon Server connections. This ensures all framework decorators (such as
 * {@code PayloadConvertingQueryBusConnector}) are applied automatically.
 * <p>
 * The connector manages tenant-specific connectors in response to tenant lifecycle events via the
 * {@link MultiTenantAwareComponent} interface. When a tenant is registered, a new
 * {@link AxonServerQueryBusConnector} is created for that tenant's context. Existing query subscriptions
 * are automatically replayed to the new connector.
 * <p>
 * Usage:
 * <pre>{@code
 * MultiTenantAxonServerQueryBusConnector connector = MultiTenantAxonServerQueryBusConnector.builder()
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
 * @see QueryBusConnector
 * @see MultiTenantAwareComponent
 * @see AxonServerQueryBusConnector
 */
public class MultiTenantAxonServerQueryBusConnector implements QueryBusConnector, MultiTenantAwareComponent {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantAxonServerQueryBusConnector.class);

    private final AxonServerConnectionManager connectionManager;
    private final AxonServerConfiguration axonServerConfiguration;
    private final TargetTenantResolver<Message> targetTenantResolver;

    private final Map<TenantDescriptor, AxonServerQueryBusConnector> connectors = new ConcurrentHashMap<>();
    private final Set<QualifiedName> subscriptions = ConcurrentHashMap.newKeySet();

    private volatile Handler incomingHandler;

    /**
     * Instantiate a {@link MultiTenantAxonServerQueryBusConnector} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantAxonServerQueryBusConnector}.
     */
    protected MultiTenantAxonServerQueryBusConnector(Builder builder) {
        builder.validate();
        this.connectionManager = builder.connectionManager;
        this.axonServerConfiguration = builder.axonServerConfiguration;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Creates a {@link MultiTenantAxonServerQueryBusConnector} from the given {@link Configuration}.
     *
     * @param config The Axon {@link Configuration} to obtain components from.
     * @return A new connector instance, or {@code null} if required components are not available.
     */
    @Nullable
    public static MultiTenantAxonServerQueryBusConnector createFrom(@Nonnull Configuration config) {
        return config.getOptionalComponent(AxonServerConnectionManager.class)
                     .flatMap(connectionManager -> config.getOptionalComponent(TargetTenantResolver.class)
                             .map(resolver -> MultiTenantAxonServerQueryBusConnector.builder()
                                     .connectionManager(connectionManager)
                                     .axonServerConfiguration(config.getComponent(AxonServerConfiguration.class))
                                     .targetTenantResolver(resolver)
                                     .build()))
                     .orElse(null);
    }

    /**
     * Instantiate a builder to construct a {@link MultiTenantAxonServerQueryBusConnector}.
     *
     * @return A Builder to create a {@link MultiTenantAxonServerQueryBusConnector}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        TenantDescriptor tenant = targetTenantResolver.resolveTenant(query, connectors.keySet());
        AxonServerQueryBusConnector connector = connectors.get(tenant);
        if (connector == null) {
            return MessageStream.failed(NoSuchTenantException.forTenantId(tenant.tenantId()));
        }
        return connector.query(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                  @Nullable ProcessingContext context,
                                                                  int updateBufferSize) {
        TenantDescriptor tenant = targetTenantResolver.resolveTenant(query, connectors.keySet());
        AxonServerQueryBusConnector connector = connectors.get(tenant);
        if (connector == null) {
            return MessageStream.failed(NoSuchTenantException.forTenantId(tenant.tenantId()));
        }
        return connector.subscriptionQuery(query, context, updateBufferSize);
    }

    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QualifiedName name) {
        logger.debug("Subscribing to query [{}] across all tenants", name);
        subscriptions.add(name);

        if (connectors.isEmpty()) {
            // No tenants registered yet; subscription will be replayed when tenants join
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(
                connectors.values().stream()
                          .map(connector -> connector.subscribe(name))
                          .toArray(CompletableFuture[]::new)
        );
    }

    @Override
    public boolean unsubscribe(@Nonnull QualifiedName name) {
        subscriptions.remove(name);

        boolean allUnsubscribed = true;
        for (AxonServerQueryBusConnector connector : connectors.values()) {
            if (!connector.unsubscribe(name)) {
                allUnsubscribed = false;
            }
        }
        return allUnsubscribed;
    }

    @Override
    public void onIncomingQuery(@Nonnull Handler handler) {
        this.incomingHandler = handler;
        connectors.values().forEach(connector -> connector.onIncomingQuery(handler));
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        logger.debug("Registering tenant [{}] for query routing", tenantDescriptor.tenantId());

        AxonServerConnection connection = connectionManager.getConnection(tenantDescriptor.tenantId());
        AxonServerQueryBusConnector connector = new AxonServerQueryBusConnector(
                connection,
                axonServerConfiguration
        );
        connectors.put(tenantDescriptor, connector);

        // Set incoming handler if already configured
        if (incomingHandler != null) {
            connector.onIncomingQuery(incomingHandler);
        }

        connector.start();

        return () -> {
            logger.debug("Unregistering tenant [{}] from query routing", tenantDescriptor.tenantId());
            AxonServerQueryBusConnector removed = connectors.remove(tenantDescriptor);
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
        AxonServerQueryBusConnector connector = connectors.get(tenantDescriptor);
        if (connector != null) {
            subscriptions.forEach(queryName -> {
                logger.debug("Replaying subscription [{}] to tenant [{}]",
                             queryName, tenantDescriptor.tenantId());
                connector.subscribe(queryName);
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
        logger.debug("Shutting down {} query bus connectors", connectors.size());
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
    Map<TenantDescriptor, AxonServerQueryBusConnector> connectors() {
        return Collections.unmodifiableMap(connectors);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("connectionManager", connectionManager);
        descriptor.describeProperty("tenants", connectors.keySet());
        descriptor.describeProperty("subscriptions", subscriptions);
    }

    /**
     * Builder class to instantiate a {@link MultiTenantAxonServerQueryBusConnector}.
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
         * Sets the {@link TargetTenantResolver} used to resolve which tenant a query belongs to.
         *
         * @param targetTenantResolver The resolver that determines the target tenant for each query.
         * @return The current builder instance, for fluent interfacing.
         */
        @SuppressWarnings("unchecked")
        public Builder targetTenantResolver(@Nonnull TargetTenantResolver<? extends Message> targetTenantResolver) {
            assertNonNull(targetTenantResolver, "TargetTenantResolver may not be null");
            this.targetTenantResolver = (TargetTenantResolver<Message>) targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantAxonServerQueryBusConnector} as specified through this Builder.
         *
         * @return A {@link MultiTenantAxonServerQueryBusConnector} as specified through this Builder.
         */
        public MultiTenantAxonServerQueryBusConnector build() {
            return new MultiTenantAxonServerQueryBusConnector(this);
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
