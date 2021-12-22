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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


/*
@author Steven van Beelen
@author Stefan Dragisic
 */

public class MultiTenantEventStore implements EventStore, MultiTenantAwareComponent {

    private final Map<TenantDescriptor, EventStore> tenantSegments = new ConcurrentHashMap<>();
    private final List<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();

    private final List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final List<Registration> dispatchInterceptorsRegistration = new CopyOnWriteArrayList<>();

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
        EventStore tenantSegment = resolveSegment();
        if (tenantSegment != null) {
            tenantSegment.publish(events);
        } else {
            resolveTenant(events.get(0))
                    .publish(events); //todo otherway around
        } //todo add corelation data provider
    }

    @Override
    public void publish(EventMessage<?>... events) {
        EventStore tenantSegment = resolveSegment();
        if (tenantSegment != null) {
            tenantSegment.publish(events);
        } else {
            resolveTenant(events[0])
                    .publish(events);
        }
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);

        tenantSegments.forEach((tenant, segment) ->
                subscribeRegistrations.putIfAbsent(tenant, segment.subscribe(messageProcessor)));

        return () -> subscribeRegistrations.values().stream().map(Registration::cancel).reduce((prev, acc) -> prev && acc).orElse(false);
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        tenantSegments.forEach((tenant, bus) ->
                dispatchInterceptorsRegistration.add(bus.registerDispatchInterceptor(dispatchInterceptor)));

        return () -> dispatchInterceptorsRegistration.stream().map(Registration::cancel).reduce((prev, acc) -> prev && acc).orElse(false);
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
                                                 dispatchInterceptorsRegistration.add(tenantSegment.registerDispatchInterceptor(
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
     * @param tenantDescriptor
     * @return
     */
    public EventStore tenantSegment(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor);
    }

    public static class Builder {

        public TenantEventSegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<Message<?>> targetTenantResolver;

        /**
         * @param tenantSegmentFactory
         * @return
         */
        public Builder tenantSegmentFactory(TenantEventSegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory, "");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * @param targetTenantResolver
         * @return
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
            // todo
        }
    }
}
