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
package org.axonframework.extension.multitenancy.messaging.eventhandling.processing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorHandler;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.MaxSegmentProvider;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.tracing.EventProcessorSpanFactory;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.eventstreaming.TrackingTokenSource;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.monitoring.MessageMonitor;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Configuration class for a multi-tenant {@link org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor}.
 * <p>
 * Extends {@link PooledStreamingEventProcessorConfiguration} to add multi-tenant specific configuration options,
 * primarily the ability to configure a {@link TenantTokenStoreFactory} for per-processor token store customization.
 * <p>
 * This allows different multi-tenant processors to use different token store strategies, overriding the globally
 * configured {@link TenantTokenStoreFactory} when needed.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see PooledStreamingEventProcessorConfiguration
 * @see TenantTokenStoreFactory
 */
public class MultiTenantPooledStreamingEventProcessorConfiguration extends PooledStreamingEventProcessorConfiguration {

    private TenantTokenStoreFactory tenantTokenStoreFactory;

    /**
     * Constructs a new {@code MultiTenantPooledStreamingEventProcessorConfiguration} with just default values.
     */
    public MultiTenantPooledStreamingEventProcessorConfiguration() {
        super();
    }

    /**
     * Constructs a new {@code MultiTenantPooledStreamingEventProcessorConfiguration} copying properties from the given
     * configuration.
     *
     * @param base The {@link EventProcessorConfiguration} to copy properties from.
     */
    public MultiTenantPooledStreamingEventProcessorConfiguration(@Nonnull EventProcessorConfiguration base) {
        super(base);
    }

    /**
     * Constructs a new {@code MultiTenantPooledStreamingEventProcessorConfiguration} with default values and retrieve
     * global default values.
     *
     * @param base          The {@link EventProcessorConfiguration} to copy properties from.
     * @param configuration The configuration, used to retrieve global default values.
     */
    public MultiTenantPooledStreamingEventProcessorConfiguration(
            @Nonnull EventProcessorConfiguration base,
            @Nonnull Configuration configuration
    ) {
        super(base, configuration);
    }

    /**
     * Sets the {@link TenantTokenStoreFactory} to use for creating per-tenant token stores.
     * <p>
     * If not set, the globally configured {@link TenantTokenStoreFactory} will be used.
     * If no global factory is configured, an {@link InMemoryTenantTokenStoreFactory} is used as default.
     *
     * @param tenantTokenStoreFactory The factory to create per-tenant token stores.
     * @return The current instance, for fluent interfacing.
     */
    public MultiTenantPooledStreamingEventProcessorConfiguration tenantTokenStoreFactory(
            @Nonnull TenantTokenStoreFactory tenantTokenStoreFactory
    ) {
        this.tenantTokenStoreFactory = Objects.requireNonNull(tenantTokenStoreFactory,
                "TenantTokenStoreFactory may not be null");
        return this;
    }

    /**
     * Returns the configured {@link TenantTokenStoreFactory}, or {@code null} if not explicitly set.
     *
     * @return The configured {@link TenantTokenStoreFactory}, or {@code null}.
     */
    public TenantTokenStoreFactory tenantTokenStoreFactory() {
        return tenantTokenStoreFactory;
    }

    // Override all fluent methods from parent to return this type for proper method chaining

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration errorHandler(@Nonnull ErrorHandler errorHandler) {
        super.errorHandler(errorHandler);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage> messageMonitor
    ) {
        super.messageMonitor(messageMonitor);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration spanFactory(
            @Nonnull EventProcessorSpanFactory spanFactory
    ) {
        super.spanFactory(spanFactory);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration unitOfWorkFactory(
            @Nonnull UnitOfWorkFactory unitOfWorkFactory
    ) {
        super.unitOfWorkFactory(unitOfWorkFactory);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration eventSource(
            @Nonnull StreamableEventSource eventSource
    ) {
        super.eventSource(eventSource);
        return this;
    }

    @Nonnull
    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration withInterceptor(
            @Nonnull MessageHandlerInterceptor<? super EventMessage> interceptor
    ) {
        super.withInterceptor(interceptor);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration tokenStore(@Nonnull TokenStore tokenStore) {
        super.tokenStore(tokenStore);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration coordinatorExecutor(
            @Nonnull ScheduledExecutorService coordinatorExecutor
    ) {
        super.coordinatorExecutor(coordinatorExecutor);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration workerExecutor(
            @Nonnull ScheduledExecutorService workerExecutor
    ) {
        super.workerExecutor(workerExecutor);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration initialSegmentCount(int initialSegmentCount) {
        super.initialSegmentCount(initialSegmentCount);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration initialToken(
            @Nonnull Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken
    ) {
        super.initialToken(initialToken);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration tokenClaimInterval(long tokenClaimInterval) {
        super.tokenClaimInterval(tokenClaimInterval);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration maxClaimedSegments(int maxClaimedSegments) {
        super.maxClaimedSegments(maxClaimedSegments);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration maxSegmentProvider(
            @Nonnull MaxSegmentProvider maxSegmentProvider
    ) {
        super.maxSegmentProvider(maxSegmentProvider);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration claimExtensionThreshold(
            long claimExtensionThreshold
    ) {
        super.claimExtensionThreshold(claimExtensionThreshold);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration batchSize(int batchSize) {
        super.batchSize(batchSize);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration clock(@Nonnull Clock clock) {
        super.clock(clock);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration enableCoordinatorClaimExtension() {
        super.enableCoordinatorClaimExtension();
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration ignoredMessageHandler(
            Consumer<? super EventMessage> ignoredMessageHandler
    ) {
        super.ignoredMessageHandler(ignoredMessageHandler);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration eventCriteria(
            @Nonnull Function<Set<QualifiedName>, EventCriteria> eventCriteriaProvider
    ) {
        super.eventCriteria(eventCriteriaProvider);
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorConfiguration schedulingProcessingContextProvider(
            @Nonnull Supplier<ProcessingContext> schedulingProcessingContextProvider
    ) {
        super.schedulingProcessingContextProvider(schedulingProcessingContextProvider);
        return this;
    }
}
