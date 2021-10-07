package org.axonframework.extensions.multitenancy.eventhandeling;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.extensions.multitenancy.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.commandbus.TenantDescriptor;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.ArrayList;
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
    private final Map<TenantDescriptor, Registration> registrations = new ConcurrentHashMap<>();

    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final List<Registration> handlerInterceptorsRegistration = new CopyOnWriteArrayList<>();

    private final String name;
    private final TenantEventProcessorSegmentFactory tenantSegmentFactory;

    /**
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param builder
     */
    protected MultiTenantEventProcessor(Builder builder) {
        builder.validate();
        this.name = builder.name;
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
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
    public void start() {
        tenantSegments.values().forEach(EventProcessor::start);
    }

    @Override
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
                handlerInterceptorsRegistration.add(bus.registerHandlerInterceptor(handlerInterceptor)));

        return () -> handlerInterceptorsRegistration.stream()
                .map(Registration::cancel)
                .reduce((prev, acc) -> prev && acc)
                .orElse(false);
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        EventProcessor tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            EventProcessor delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    @Override
    public Registration registerTenantAndSubscribe(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, k -> {
            EventProcessor tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            handlerInterceptors.forEach(handlerInterceptor ->
                    handlerInterceptorsRegistration.add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

            tenantSegment.start();

            return tenantSegment;
        });

        return () -> {
            EventProcessor delegate = unregisterTenant(tenantDescriptor);
            if (delegate != null) {
                delegate.shutDown();
                return true;
            }
            return false;
        };
    }

    /**
     * @return
     */
    public List<EventProcessor> tenantSegments() {
        return Collections.unmodifiableList(new ArrayList<>(tenantSegments.values()));
    }

    private EventProcessor unregisterTenant(TenantDescriptor tenantDescriptor) {
        registrations.remove(tenantDescriptor).cancel();
        return tenantSegments.remove(tenantDescriptor);
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
