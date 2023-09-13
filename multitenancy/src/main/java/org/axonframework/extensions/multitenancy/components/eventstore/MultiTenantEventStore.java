/*
 * Copyright (c) 2010-2023. Axon Framework
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
package org.axonframework.extensions.multitenancy.components.eventstore;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.MultiStreamableMessageSource;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.MultiTenantDispatchInterceptorSupport;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;


/**
 * Tenant aware implementation of the {@link EventStore}.
 * <p>
 * Tenant-specific {@code EventStore} segments are either resolved from the
 * {@link EventMessage#getMetaData() event's metadata} or from the expected existence of the
 * {@link org.axonframework.messaging.unitofwork.UnitOfWork}. The {@link #openStream(TrackingToken)} operation defaults
 * to returning a {@link MultiStreamableMessageSource} combining all tenant segments.
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class MultiTenantEventStore implements
        EventStore,
        MultiTenantAwareComponent,
        MultiTenantDispatchInterceptorSupport<EventMessage<?>, EventStore> {

    private final Map<TenantDescriptor, EventStore> tenantSegments = new ConcurrentHashMap<>();
    private final List<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, Registration> subscribeRegistrations = new ConcurrentHashMap<>();
    private final List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> dispatchInterceptorsRegistration = new ConcurrentHashMap<>();

    private final TenantEventSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<Message<?>> targetTenantResolver;

    private MultiStreamableMessageSource multiSource;

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
    public void publish(List<? extends EventMessage<?>> events) {
        events.stream().findFirst()
              .map(this::resolveTenant)
              .orElseGet(this::resolveSegment)
              .publish(events);
    }

    @Override
    public void publish(EventMessage<?>... events) {
        Optional.ofNullable(events[0])
                .map(this::resolveTenant)
                .orElseGet(this::resolveSegment)
                .publish(events);
    }

    @Override
    public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);

        tenantSegments.forEach((tenant, segment) -> subscribeRegistrations.putIfAbsent(
                tenant, segment.subscribe(messageProcessor)
        ));

        return () -> subscribeRegistrations.values()
                                           .stream()
                                           .map(Registration::cancel)
                                           .reduce((prev, acc) -> prev && acc)
                                           .orElse(false);
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

    private EventStore unregisterTenant(TenantDescriptor tenantDescriptor) {
        //noinspection resource
        Registration remove = subscribeRegistrations.remove(tenantDescriptor);
        if (remove != null) {
            remove.cancel();
        }
        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, k -> {
            EventStore tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            dispatchInterceptors.forEach(
                    dispatchInterceptor ->
                            dispatchInterceptorsRegistration
                                    .computeIfAbsent(tenantDescriptor, t -> new CopyOnWriteArrayList<>())
                                    .add(tenantSegment.registerDispatchInterceptor(dispatchInterceptor))
            );

            messageProcessors.forEach(processor -> subscribeRegistrations.putIfAbsent(
                    tenantDescriptor, tenantSegment.subscribe(processor)
            ));

            return tenantSegment;
        });

        return () -> {
            EventStore delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    private EventStore resolveTenantSilently(Message<?> eventMessage) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(eventMessage, tenantSegments.keySet());
        return tenantSegments.get(tenantDescriptor);
    }

    private EventStore resolveTenant(Message<?> eventMessage) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(eventMessage, tenantSegments.keySet());
        EventStore tenantEventStore = tenantSegments.get(tenantDescriptor);
        if (tenantEventStore == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantEventStore;
    }

    private EventStore resolveSegment() {
        return resolveTenantSilently(CurrentUnitOfWork.get().getMessage());
    }

    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier) {
        return resolveSegment()
                .readEvents(aggregateIdentifier);
    }

    /**
     * Open an event stream with the {@link EventStore} segment of the given {@code tenantDescriptor}, containing all
     * domain events belonging to the given {@code aggregateIdentifier}.
     * <p>
     * The returned stream is <em>finite</em>, ending with the last known event of the aggregate. If the event store
     * holds no events of the given aggregate an empty stream is returned.
     *
     * @param aggregateIdentifier the identifier of the aggregate whose events to fetch
     * @param tenantDescriptor    The {@link TenantDescriptor} referring to the {@link EventStore} segment to read an
     *                            aggregate event stream from.
     * @return a stream of all currently stored events of the aggregate
     */
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier,
                                        @Nonnull TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor)
                             .readEvents(aggregateIdentifier);
    }

    @Override
    public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        resolveSegment()
                .storeSnapshot(snapshot);
    }

    /**
     * Stores the given (temporary) {@code snapshot} event with the {@link EventStore} segment of the given
     * {@code tenantDescriptor}, when present. This snapshot replaces the segment of the event stream identified by the
     * {@code snapshot}'s {@link DomainEventMessage#getAggregateIdentifier() Aggregate Identifier} up to (and including)
     * the event with the {@code snapshot}'s {@link DomainEventMessage#getSequenceNumber() sequence number}.
     * <p>
     * These snapshots will only affect the {@link DomainEventStream} returned by the {@link #readEvents(String)}
     * method. They do not change the events returned by {@link EventStore#openStream(TrackingToken)} or those received
     * by using {@link #subscribe(java.util.function.Consumer)}.
     * <p>
     * Note that snapshots are considered a temporary replacement for Events, and are used as performance optimization.
     * Event Store implementations may choose to ignore or delete snapshots.
     *
     * @param snapshot         The snapshot to replace part of the DomainEventStream.
     * @param tenantDescriptor The {@link TenantDescriptor} referring to the {@link EventStore} segment to store a
     *                         snapshot in.
     */
    public void storeSnapshot(DomainEventMessage<?> snapshot, TenantDescriptor tenantDescriptor) {
        tenantSegments.get(tenantDescriptor)
                      .storeSnapshot(snapshot);
    }

    @Override
    public BlockingStream<TrackedEventMessage<?>> openStream(TrackingToken trackingToken) {
        return multiSource().openStream(trackingToken);
    }

    private MultiStreamableMessageSource multiSource() {
        if (Objects.isNull(multiSource)) {
            MultiStreamableMessageSource.Builder sourceBuilder = MultiStreamableMessageSource.builder();
            tenantSegments.forEach((key, value) -> sourceBuilder.addMessageSource(key.tenantId(), value));
            this.multiSource = sourceBuilder.build();
        }
        return multiSource;
    }

    @Override
    public TrackingToken createTailToken() {
        return multiSource().createTailToken();
    }

    @Override
    public TrackingToken createHeadToken() {
        return multiSource().createHeadToken();
    }

    @Override
    public TrackingToken createTokenAt(Instant dateTime) {
        return multiSource().createTokenAt(dateTime);
    }

    @Override
    public TrackingToken createTokenSince(Duration duration) {
        return multiSource().createTokenSince(duration);
    }

    @Override
    public Map<TenantDescriptor, EventStore> tenantSegments() {
        return tenantSegments;
    }

    @Override
    public List<MessageDispatchInterceptor<? super EventMessage<?>>> getDispatchInterceptors() {
        return dispatchInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getDispatchInterceptorsRegistration() {
        return dispatchInterceptorsRegistration;
    }

    /**
     * Builder class to instantiate a {@link MultiTenantEventStore}.
     * <p>
     * The {@link TenantEventSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     */
    public static class Builder {

        protected TenantEventSegmentFactory tenantSegmentFactory;
        protected TargetTenantResolver<Message<?>> targetTenantResolver;

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
        public Builder targetTenantResolver(TargetTenantResolver<Message<?>> targetTenantResolver) {
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
         *          *                                    specifications.
         */
        protected void validate() {
            assertNonNull(tenantSegmentFactory,
                          "The TenantEventProcessorSegmentFactory is a hard requirement and should be provided");
            assertNonNull(targetTenantResolver,
                          "The TargetTenantResolver is a hard requirement and should be provided");
        }
    }
}
