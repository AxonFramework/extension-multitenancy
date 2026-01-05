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
package org.axonframework.extensions.multitenancy.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.extensions.multitenancy.core.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.core.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.core.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Tenant aware implementation of the {@link EventStore}.
 * <p>
 * Tenant-specific {@code EventStore} segments are resolved from the {@link EventMessage#metadata() event's metadata}.
 * The {@link #open(StreamingCondition, ProcessingContext)} operation throws an
 * {@link UnsupportedOperationException} as multi-tenant streaming requires combining streams from all tenants,
 * which should be handled at a higher level.
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public class MultiTenantEventStore implements EventStore, MultiTenantAwareComponent, TenantEventStoreProvider {

    /**
     * The order in which the {@link MultiTenantEventStore} is applied as a decorator to the {@link EventStore}.
     * <p>
     * Uses a lower order than {@link InterceptingEventStore} (which is at {@code Integer.MIN_VALUE + 50})
     * to ensure correlation data (including tenant info) is applied BEFORE multi-tenant routing.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 25;

    private final Map<TenantDescriptor, EventStore> tenantSegments = new ConcurrentHashMap<>();
    private final List<BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>>> eventsBatchConsumers =
            new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, Registration> subscribeRegistrations = new ConcurrentHashMap<>();

    private final TenantEventSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<Message> targetTenantResolver;

    /**
     * Instantiate a {@link MultiTenantEventStore} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantEventStore} instance with.
     */
    protected MultiTenantEventStore(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Instantiate a builder to be able to construct a {@link MultiTenantEventStore}
     * <p>
     * The {@link TenantEventSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @return A Builder to be able to create a {@link MultiTenantEventStore}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        EventStore tenantSegment = resolveTenant(events.get(0));
        return tenantSegment.publish(context, events);
    }

    @Override
    public Registration subscribe(
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
        eventsBatchConsumers.add(eventsBatchConsumer);

        tenantSegments.forEach((tenant, segment) -> subscribeRegistrations.computeIfAbsent(
                tenant, t -> segment.subscribe(eventsBatchConsumer)
        ));

        return () -> {
            eventsBatchConsumers.remove(eventsBatchConsumer);
            return subscribeRegistrations.values()
                                         .stream()
                                         .map(Registration::cancel)
                                         .reduce((prev, acc) -> prev && acc)
                                         .orElse(false);
        };
    }

    @Override
    public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition,
                                            @Nullable ProcessingContext context) {
        throw new UnsupportedOperationException(
                "Multi-tenant event streaming is not directly supported. "
                        + "Use individual tenant segments or implement a multi-source stream combiner."
        );
    }

    @Override
    public EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext) {
        Message message = Message.fromContext(processingContext);
        if (message == null) {
            throw new IllegalStateException(
                    "Cannot create multi-tenant EventStoreTransaction: no message found in ProcessingContext. "
                    + "Ensure commands are dispatched through MultiTenantCommandBus, which adds the command "
                    + "message to the context for tenant resolution. If you're using a custom setup, wrap your "
                    + "CommandBus segments with TenantAwareCommandBus or manually add the message to the context "
                    + "using Message.addToContext()."
            );
        }

        TenantDescriptor tenant = targetTenantResolver.resolveTenant(message, tenantSegments.keySet());
        EventStore tenantEventStore = tenantSegments.get(tenant);
        if (tenantEventStore == null) {
            throw NoSuchTenantException.forTenantId(tenant.tenantId());
        }

        return tenantEventStore.transaction(processingContext);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
        throw new UnsupportedOperationException(
                "Multi-tenant token operations are not directly supported. "
                        + "Use individual tenant segments."
        );
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
        throw new UnsupportedOperationException(
                "Multi-tenant token operations are not directly supported. "
                        + "Use individual tenant segments."
        );
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context) {
        throw new UnsupportedOperationException(
                "Multi-tenant token operations are not directly supported. "
                        + "Use individual tenant segments."
        );
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("tenantSegments", tenantSegments);
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        EventStore tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            EventStore delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, k -> {
            EventStore tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            eventsBatchConsumers.forEach(consumer -> subscribeRegistrations.computeIfAbsent(
                    tenantDescriptor, t -> tenantSegment.subscribe(consumer)
            ));

            return tenantSegment;
        });

        return () -> {
            EventStore delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    private EventStore unregisterTenant(TenantDescriptor tenantDescriptor) {
        Registration remove = subscribeRegistrations.remove(tenantDescriptor);
        if (remove != null) {
            remove.cancel();
        }
        return tenantSegments.remove(tenantDescriptor);
    }

    private EventStore resolveTenant(Message message) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(message, tenantSegments.keySet());
        EventStore tenantEventStore = tenantSegments.get(tenantDescriptor);
        if (tenantEventStore == null) {
            throw NoSuchTenantException.forTenantId(tenantDescriptor.tenantId());
        }
        return tenantEventStore;
    }

    /**
     * Returns the tenant segments managed by this {@link MultiTenantEventStore}.
     *
     * @return The tenant segments managed by this {@link MultiTenantEventStore}.
     */
    public Map<TenantDescriptor, EventStore> tenantSegments() {
        return tenantSegments;
    }

    /**
     * Builder class to instantiate a {@link MultiTenantEventStore}.
     * <p>
     * The {@link TenantEventSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     */
    public static class Builder {

        protected TenantEventSegmentFactory tenantSegmentFactory;
        protected TargetTenantResolver<Message> targetTenantResolver;

        /**
         * Sets the {@link TenantEventSegmentFactory} used to build {@link EventStore} segment for given
         * {@link TenantDescriptor}.
         *
         * @param tenantSegmentFactory tenant aware segment factory
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tenantSegmentFactory(TenantEventSegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory, "The TenantEventSegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message}
         * message
         *
         * @param targetTenantResolver used to resolve correct tenant segment based on {@link Message} message
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder targetTenantResolver(TargetTenantResolver<Message> targetTenantResolver) {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantEventStore} as specified through this Builder.
         *
         * @return a {@link MultiTenantEventStore} as specified through this Builder
         */
        public MultiTenantEventStore build() {
            return new MultiTenantEventStore(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(tenantSegmentFactory,
                          "The TenantEventSegmentFactory is a hard requirement and should be provided");
            assertNonNull(targetTenantResolver,
                          "The TargetTenantResolver is a hard requirement and should be provided");
        }
    }
}
