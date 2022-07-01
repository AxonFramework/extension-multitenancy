/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.multitenancy.components.eventstore;

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.MultiStreamableMessageSource;
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

import static org.axonframework.common.BuilderUtils.assertNonNull;


/**
 * Multi tenant implementation of the {@link EventStore} that delegates to a single {@link EventStore} instance.
 * <p>
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 */

public class MultiTenantEventStore
        implements MultiTenantDispatchInterceptorSupport<EventMessage<?>, EventStore>, EventStore,
        MultiTenantAwareComponent {

    private final Map<TenantDescriptor, EventStore> tenantSegments = new ConcurrentHashMap<>();
    private final List<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();

    private final List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> dispatchInterceptorsRegistration = new ConcurrentHashMap<>();

    private final Map<TenantDescriptor, Registration> subscribeRegistrations = new ConcurrentHashMap<>();

    private final TenantEventSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<Message<?>> targetTenantResolver;

    private MultiStreamableMessageSource multiSource;

    public MultiTenantEventStore(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

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
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);

        tenantSegments.forEach((tenant, segment) ->
                                       subscribeRegistrations.putIfAbsent(tenant, segment.subscribe(messageProcessor)));

        return () -> subscribeRegistrations.values().stream().map(Registration::cancel).reduce((prev, acc) -> prev && acc).orElse(false);
    }

    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        EventStore tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            EventStore delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    public EventStore unregisterTenant(TenantDescriptor tenantDescriptor) {
        Registration remove = subscribeRegistrations.remove(tenantDescriptor);
        if (remove != null) remove.cancel();
        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, k -> {
            EventStore tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            dispatchInterceptors.forEach(dispatchInterceptor ->
                                                 dispatchInterceptorsRegistration
                                                         .computeIfAbsent(tenantDescriptor,
                                                                          t -> new CopyOnWriteArrayList<>())
                                                         .add(tenantSegment.registerDispatchInterceptor(
                                                                 dispatchInterceptor)));

            messageProcessors.forEach(processor ->
                                              subscribeRegistrations.putIfAbsent(tenantDescriptor,
                                                                                 tenantSegment.subscribe(processor)));

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
    public DomainEventStream readEvents(String aggregateIdentifier) {
        return resolveSegment()
                .readEvents(aggregateIdentifier);
    }

    public DomainEventStream readEvents(String aggregateIdentifier, TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor)
                             .readEvents(aggregateIdentifier);
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        resolveSegment()
                .storeSnapshot(snapshot);
    }

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

    /**
     * @param tenantDescriptor the tenant descriptor to resolve the segment.
     * @return Returns registered tenant segment for given {@link TenantDescriptor}.
     */
    public EventStore tenantSegment(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor);
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

    public static class Builder {

        public TenantEventSegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<Message<?>> targetTenantResolver;

        /**
         * Sets the {@link TenantEventSegmentFactory} used to build {@link EventStore} segment for given {@link TenantDescriptor}.
         *
         * @param tenantSegmentFactory tenant aware segment factory
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tenantSegmentFactory(TenantEventSegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory, "The TenantEventSegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message} message
         *
         * @param targetTenantResolver used to resolve correct tenant segment based on {@link Message} message
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder targetTenantResolver(TargetTenantResolver<Message<?>> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        public MultiTenantEventStore build() {
            return new MultiTenantEventStore(this);
        }

        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
        }
    }
}
