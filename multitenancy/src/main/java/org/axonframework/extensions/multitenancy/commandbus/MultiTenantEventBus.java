package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/*
@author Steven van Beelen
@author Stefan Dragisic
 */

public class MultiTenantEventBus implements EventBus {

    private final Map<TenantDescriptor, EventBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, MessageHandler<? super EventMessage<?>>> handlers = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Registration>> tenantRegistrations = new ConcurrentHashMap<>();

    private final TenantEventSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<EventMessage<?>> targetTenantResolver;

    public MultiTenantEventBus(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    public static Builder builder() {
        return new Builder();
    }


    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        EventBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            EventBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
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

        public MultiTenantEventBus build() {
            return new MultiTenantEventBus(this);
        }

        protected void validate() {
            // todo
        }
    }
}
