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

package org.axonframework.extensions.multitenancy.components.eventhandeling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.MultiTenantHandlerInterceptorSupport;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
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
 * Multi tenant implementation of {@link EventProcessor} that encapsulates the actual {@link EventProcessor}s, and
 * forwards corresponding actions to the correct tenant.
 *
 * @author Stefan Dragisic
 */
public class MultiTenantEventProcessor
        implements MultiTenantHandlerInterceptorSupport<EventMessage<?>, EventProcessor>, EventProcessor,
        MultiTenantAwareComponent {

    private final Map<TenantDescriptor, EventProcessor> tenantEventProcessorsSegments = new ConcurrentHashMap<>();
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> handlerInterceptorsRegistration = new ConcurrentHashMap<>();
    private final String name;
    private final TenantEventProcessorSegmentFactory tenantEventProcessorSegmentFactory;
    private volatile boolean started = false;

    /**
     * Instantiate a {@link MultiTenantEventProcessor} based on the fields contained in the {@link
     * MultiTenantEventProcessor.Builder}.
     * <p>
     * Will assert the following for their presence prior to constructing this processor:
     * <ul>
     *     <li>The Event Processor's {@code name}.</li>
     *     <li>An {@link TenantEventProcessorSegmentFactory}.</li>
     * </ul>
     * If any of these is not present or does no comply to the requirements an {@link AxonConfigurationException} is thrown.
     *
     * @param builder the {@link MultiTenantEventProcessor.Builder} used to instantiate a {@link
     *                MultiTenantEventProcessor} instance
     */
    protected MultiTenantEventProcessor(Builder builder) {
        builder.validate();
        this.name = builder.name;
        this.tenantEventProcessorSegmentFactory = builder.tenantEventProcessorSegmentFactory;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MultiTenantEventProcessor}. The following fields of this
     * builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     *     <li>The Event Processor's {@code name}.</li>
     *     <li>An {@link TenantEventProcessorSegmentFactory}.</li>
     * </ul>
     *
     * @return a Builder to be able to create a {@link MultiTenantEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the name of this event processor. This name is used to detect distributed instances of the same event
     * processor. Multiple instances referring to the same logical event processor (on different JVM's) must have the
     * same name.
     *
     * @return the name of this event processor
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<TenantDescriptor, EventProcessor> tenantSegments() {
        return tenantEventProcessorsSegments;
    }

    /**
     * Return the list of already registered {@link MessageHandlerInterceptor}s for this event processor. To register a
     * new interceptor use {@link EventProcessor#registerHandlerInterceptor(MessageHandlerInterceptor)}
     *
     * @return the list of registered interceptors of this event processor
     */
    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return handlerInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getHandlerInterceptorsRegistration() {
        return handlerInterceptorsRegistration;
    }

    /**
     * Start processing events.
     */
    @Override
    @StartHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void start() {
        started = true;
        tenantEventProcessorsSegments.values().forEach(EventProcessor::start);
    }

    /**
     * Stops processing events. Blocks until the shutdown is complete.
     */
    @Override
    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void shutDown() {
        started = false;
        tenantEventProcessorsSegments.values().forEach(EventProcessor::shutDown);
    }

    /**
     * Indicates whether multi tenant processor is currently running (is started). For specific tenant status, see
     * {@link #isRunning(TenantDescriptor)}
     *
     * @return {@code true} when running, otherwise {@code false}
     */
    @Override
    public boolean isRunning() {
        return started;
    }

    /**
     * Indicates whether specific tenant processor is currently running (i.e. consuming events from its message
     * source).
     *
     * @param tenantDescriptor the tenant descriptor
     * @return {@code true} when running, otherwise {@code false}
     */
    public boolean isRunning(TenantDescriptor tenantDescriptor) {
        return tenantEventProcessorsSegments.get(tenantDescriptor).isRunning();
    }

    /**
     * This particular the processor is never shut down due to an error. Check {@link #isError(TenantDescriptor)}} to
     * see if the tenant processor has error.
     *
     * @return false
     */
    @Override
    public boolean isError() {
        return false;
    }

    /**
     * Indicates whether the tenant processor has been shut down due to an error. In such case, the processor has
     * forcefully shut down, as it wasn't able to automatically recover.
     * <p>
     * Note that this method returns {@code false} when the tenant processor was stopped using {@link #shutDown()}.
     *
     * @return {@code true} when paused due to an error, otherwise {@code false}
     */
    public boolean isError(TenantDescriptor tenantDescriptor) {
        return tenantEventProcessorsSegments.get(tenantDescriptor).isError();
    }

    /**
     * Register the given {@code tenant} as a local segment. Tenants can be only registered prior to starting the
     * processor. To register and start a tenant durring runtime, use {@link #registerAndStartTenant(TenantDescriptor)}
     *
     * @param tenantDescriptor The tenant to register
     * @return a Registration, which may be used to remove the tenant
     */
    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        if (started) {
            throw new IllegalStateException("Cannot register tenant after processor has been started");
        }
        EventProcessor tenantSegment = tenantEventProcessorSegmentFactory.apply(tenantDescriptor);
        tenantEventProcessorsSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> stopAndRemoveTenant(tenantDescriptor);
    }

    /**
     * Register the given and starts {@code tenant} immediately as a local segment.
     *
     * @param tenantDescriptor The tenant to register
     * @return a Registration, which may be used to remove the tenant
     */
    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantEventProcessorsSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            EventProcessor tenantSegment = tenantEventProcessorSegmentFactory.apply(tenant);

            handlerInterceptors.forEach(handlerInterceptor ->
                                                handlerInterceptorsRegistration
                                                        .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                        .add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

            tenantSegment.start();

            return tenantSegment;
        });

        return () -> stopAndRemoveTenant(tenantDescriptor);
    }


    /**
     * Stops the given {@code tenant} and removes it from the processor.
     *
     * @param tenantDescriptor The tenant to register
     * @return boolean indicating whether the tenant was removed
     */
    public boolean stopAndRemoveTenant(TenantDescriptor tenantDescriptor) {
        List<Registration> registrations = handlerInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) {
            registrations.forEach(Registration::cancel);
        }
        EventProcessor delegate = tenantEventProcessorsSegments.remove(tenantDescriptor);
        if (delegate != null) {
            delegate.shutDown();
            return true;
        }
        return false;
    }

    /**
     * Returns the tenants currently registered with the processor.
     *
     * @return list of tenants event processors
     */
    public List<EventProcessor> tenantEventProcessors() {
        return Collections.unmodifiableList(new ArrayList<>(tenantEventProcessorsSegments.values()));
    }


    /**
     * Builder class to instantiate a {@link MultiTenantEventProcessor}.
     * <p>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     *     <li>The Event Processor's {@code name}.</li>
     *     <li>An {@link TenantEventProcessorSegmentFactory}.</li>
     * </ul>
     */
    public static class Builder {

        private String name;
        private TenantEventProcessorSegmentFactory tenantEventProcessorSegmentFactory;

        public Builder name(String name) {
            assertNonEmpty(name, "A name should be provided");
            this.name = name;
            return this;
        }

        public Builder tenantSegmentFactory(TenantEventProcessorSegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory should not be null");
            this.tenantEventProcessorSegmentFactory = tenantSegmentFactory;
            return this;
        }

        public MultiTenantEventProcessor build() {
            return new MultiTenantEventProcessor(this);
        }

        protected void validate() {
            assertNonEmpty(name, "The name is a hard requirement");
            assertNonNull(tenantEventProcessorSegmentFactory,
                          "The TenantEventProcessorSegmentFactory is a hard requirement");
        }
    }
}
