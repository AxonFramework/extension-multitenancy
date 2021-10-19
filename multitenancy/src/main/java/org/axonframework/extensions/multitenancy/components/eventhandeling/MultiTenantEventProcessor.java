package org.axonframework.extensions.multitenancy.components.eventhandeling;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * @author Stefan Dragisic
 */
public class MultiTenantEventProcessor implements EventProcessor, MultiTenantAwareComponent {

    private final Map<TenantDescriptor, EventProcessor> tenantSegments = new ConcurrentHashMap<>();

    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> handlerInterceptorsRegistration = new ConcurrentHashMap<>();

    private final String name;
    private final TenantEventProcessorSegmentFactory tenantSegmentFactory;

    /**
     * @param builder
     */
    protected MultiTenantEventProcessor(Builder builder) {
        builder.validate();
        this.name = builder.name;
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
    }

    /**
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return Collections.unmodifiableList(handlerInterceptors);
    }

    @Override
    @StartHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void start() {
        tenantSegments.values().forEach(EventProcessor::start);
    }

    @Override
    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void shutDown() {
        tenantSegments.values().forEach(EventProcessor::shutDown);
    }

    /**
     * todo document that this only checks a single instance!!!
     *
     * @return
     */
    @Override
    public boolean isRunning() {
        return tenantSegments.values().stream().findFirst().map(EventProcessor::isRunning).orElse(false);
    }

    public boolean isRunning(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor).isRunning();
    }

    /**
     * todo document that this only checks a single instance!!!
     *
     * @return
     */
    @Override
    public boolean isError() {
        return tenantSegments.values().stream().findFirst().map(EventProcessor::isError).orElse(false);
    }

    public boolean isError(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor).isError();
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super EventMessage<?>> handlerInterceptor) {
        handlerInterceptors.add(handlerInterceptor);
        tenantSegments.forEach((tenant, bus) ->
                handlerInterceptorsRegistration
                        .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                        .add(bus.registerHandlerInterceptor(handlerInterceptor)));

        return () -> handlerInterceptorsRegistration.values()
                .stream()
                .flatMap(Collection::stream)
                .map(Registration::cancel)
                .reduce((prev, acc) -> prev && acc)
                .orElse(false);
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        EventProcessor tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> unregisterTenant(tenantDescriptor);
    }

    @Override
    public Registration registerTenantAndSubscribe(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            EventProcessor tenantSegment = tenantSegmentFactory.apply(tenant);

            handlerInterceptors.forEach(handlerInterceptor ->
                    handlerInterceptorsRegistration
                            .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                            .add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

            tenantSegment.start();

            return tenantSegment;
        });

        return () -> unregisterTenant(tenantDescriptor);
    }

    private boolean unregisterTenant(TenantDescriptor tenantDescriptor) {
        List<Registration> registrations = handlerInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) registrations.forEach(Registration::cancel);
        EventProcessor delegate = tenantSegments.remove(tenantDescriptor);
        if (delegate != null) {
            delegate.shutDown();
            return true;
        }
        return false;
    }

    /**
     * @return
     */
    public List<EventProcessor> tenantSegments() {
        return Collections.unmodifiableList(new ArrayList<>(tenantSegments.values()));
    }


    public static class Builder {

        private String name;
        private TenantEventProcessorSegmentFactory tenantSegmentFactory;

        public Builder name(String name) {
            assertNonEmpty(name, "A name should be provided");
            this.name = name;
            return this;
        }

        public Builder tenantSegmentFactory(TenantEventProcessorSegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory should not be null");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        public MultiTenantEventProcessor build() {
            return new MultiTenantEventProcessor(this);
        }

        protected void validate() {
            assertNonEmpty(name, "The name is a hard requirement");
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
        }

    }
}
