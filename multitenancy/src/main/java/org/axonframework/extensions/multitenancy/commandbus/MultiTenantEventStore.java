package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.MultiStreamableMessageSource;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/*
@author Steven van Beelen
@author Stefan Dragisic
 */

public class MultiTenantEventStore implements EventStore, MultiTenantBus {

    private final Map<TenantDescriptor, EventStore> tenantSegments = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, MessageHandler<? super EventMessage<?>>> handlers = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Registration>> tenantRegistrations = new ConcurrentHashMap<>();
    private MultiStreamableMessageSource multiSource;

    private final TenantEventSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<EventMessage<?>> targetTenantResolver;

    public MultiTenantEventStore(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    public static Builder builder() {
        return new Builder();
    }


    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        EventStore tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            EventBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    @Override
    public Registration registerTenantAndSubscribe(TenantDescriptor tenantDescriptor) {

        return null;
    }

    public EventBus unregisterTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public void publish(EventMessage<?>... events) {
        EventBus tenantEventBus = resolveTenant(events[0]); //todo better solution
        tenantEventBus.publish(events);
    }

    @Override
    public void publish(List<? extends EventMessage<?>> events) {
        EventBus tenantEventBus = resolveTenant(events.get(0)); //todo better solution
        tenantEventBus.publish(events);
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
        return null;
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        Map<String, Registration> registrationMap = tenantSegments.entrySet()
                .stream()
                .collect(Collectors.toMap(key -> key.getKey().tenantId(), entry -> entry.getValue().subscribe(messageProcessor)));

        //todo add too tenantRegistrations

        return () -> {
            //todo iterate registrationMap and cancel all
            return true;
        };
    }


    private EventBus resolveTenant(EventMessage<?> eventMessage) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(eventMessage, tenantSegments.keySet());
        EventBus tenantEventBus = tenantSegments.get(tenantDescriptor);
        if (tenantEventBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantEventBus;
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        EventStore tenantEventStore = getTenantSegment();

        return tenantEventStore.readEvents(aggregateIdentifier);
    }

//    public DomainEventStream readEvents(String aggregateIdentifier, String tenant) { todo
//        EventStore tenantEventStore = getTenantSegment();
//
//        return tenantEventStore.readEvents(aggregateIdentifier);
//    }

    private EventStore getTenantSegment() {
        TenantDescriptor tenantDescriptor = CurrentUnitOfWork.get().getResource("tenantDescriptor");
        if (Objects.isNull(tenantDescriptor)) {
            throw new AxonConfigurationException("todo");
        }

        EventStore tenantEventStore = tenantSegments.get(tenantDescriptor);
        if (Objects.isNull(tenantEventStore)) {
            throw new AxonConfigurationException("todo");
        }
        return tenantEventStore;
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        getTenantSegment().storeSnapshot(snapshot);
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

    public static class Builder {

        public TenantEventSegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<EventMessage<?>> targetTenantResolver;

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
        public Builder targetTenantResolver(TargetTenantResolver<EventMessage<?>> targetTenantResolver) {
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
