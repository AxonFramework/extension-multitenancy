/*
 * Copyright (c) 2010-2024. Axon Framework
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
 * Tenant aware implementation of {@link EventProcessor} that encapsulates the actual {@link EventProcessor}s, and
 * forwards corresponding actions to a tenant-specific segment.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class MultiTenantEventProcessor implements
        EventProcessor,
        MultiTenantAwareComponent,
        MultiTenantHandlerInterceptorSupport<EventMessage<?>, EventProcessor> {

    private final Map<TenantDescriptor, EventProcessor> tenantEventProcessorsSegments = new ConcurrentHashMap<>();
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> handlerInterceptorsRegistration = new ConcurrentHashMap<>();
    private final String name;
    private final TenantEventProcessorSegmentFactory tenantEventProcessorSegmentFactory;

    private volatile boolean started = false;

    /**
     * Instantiate a {@link MultiTenantEventProcessor} based on the fields contained in the {@link Builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantEventProcessor} instance.
     */
    protected MultiTenantEventProcessor(Builder builder) {
        builder.validate();
        this.name = builder.name;
        this.tenantEventProcessorSegmentFactory = builder.tenantEventProcessorSegmentFactory;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MultiTenantEventProcessor}.
     * <p>
     * The {@link Builder#name(String) Event Processor's name} and
     * {@link Builder#tenantSegmentFactory(TenantEventProcessorSegmentFactory) tenant segment factory} are <b>hard
     * requirements</b> and as such should be provided.
     *
     * @return A Builder to be able to create a {@link MultiTenantEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<TenantDescriptor, EventProcessor> tenantSegments() {
        return tenantEventProcessorsSegments;
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return handlerInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getHandlerInterceptorsRegistration() {
        return handlerInterceptorsRegistration;
    }

    @Override
    @StartHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void start() {
        started = true;
        tenantEventProcessorsSegments.values().forEach(EventProcessor::start);
    }

    @Override
    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void shutDown() {
        started = false;
        tenantEventProcessorsSegments.values().forEach(EventProcessor::shutDown);
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    /**
     * Indicates whether the {@link EventProcessor} for the given {@code tenantDescriptor} is currently running (i.e.
     * consuming events from its message source).
     *
     * @param tenantDescriptor The tenant descriptor referring to the {@link EventProcessor} for which to check if it is
     *                         currently running.
     * @return {@code true} when running, otherwise {@code false}.
     */
    public boolean isRunning(TenantDescriptor tenantDescriptor) {
        return tenantEventProcessorsSegments.get(tenantDescriptor).isRunning();
    }

    /**
     * This particular the processor is never shut down due to an error. Check {@link #isError(TenantDescriptor)}} to
     * see if the tenant processor has error.
     *
     * @return {@code false} in all cases, as {@link #isError(TenantDescriptor)} should be used instead.
     */
    @Override
    public boolean isError() {
        return false;
    }

    /**
     * Indicates whether the {@link EventProcessor} for the given {@code tenantDescriptor} has been shut down due to an
     * error. In such case, the processor has forcefully shut down, as it wasn't able to automatically recover.
     * <p>
     * Note that this method returns {@code false} when the tenant processor was stopped using {@link #shutDown()}.
     *
     * @return {@code true} when paused due to an error, otherwise {@code false}.
     */
    public boolean isError(TenantDescriptor tenantDescriptor) {
        return tenantEventProcessorsSegments.get(tenantDescriptor).isError();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Tenants can be only registered prior to {@link #start() starting} this processor. To register and start a tenant
     * during runtime, use {@link #registerAndStartTenant(TenantDescriptor)}
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

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantEventProcessorsSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            EventProcessor tenantSegment = tenantEventProcessorSegmentFactory.apply(tenant);

            handlerInterceptors.forEach(
                    handlerInterceptor -> handlerInterceptorsRegistration
                            .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                            .add(tenantSegment.registerHandlerInterceptor(handlerInterceptor))
            );

            tenantSegment.start();

            return tenantSegment;
        });

        return () -> stopAndRemoveTenant(tenantDescriptor);
    }


    /**
     * Stops the given {@code tenant} and removes it from this processor. Note that this does not remove any potentially
     * persisted {@link org.axonframework.eventhandling.TrackingToken TrackingTokens} from
     * {@link org.axonframework.eventhandling.StreamingEventProcessor} instances!
     *
     * @param tenantDescriptor The tenant to stop and remove from this processor.
     * @return A {@code boolean} indicating whether the tenant was removed.
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
     * Returns a list of all {@link EventProcessor} this instance manages.
     *
     * @return A list of all {@link EventProcessor} this instance manages.
     */
    public List<EventProcessor> tenantEventProcessors() {
        return Collections.unmodifiableList(new ArrayList<>(tenantEventProcessorsSegments.values()));
    }

    /**
     * Builder class to instantiate a {@link MultiTenantEventProcessor}.
     * <p>
     * The {@link Builder#name(String) Event Processor's name} and
     * {@link Builder#tenantSegmentFactory(TenantEventProcessorSegmentFactory) tenant segment factory} are <b>hard
     * requirements</b> and as such should be provided.
     */
    public static class Builder {

        private String name;
        private TenantEventProcessorSegmentFactory tenantEventProcessorSegmentFactory;

        /**
         * Sets the {@code name} of this {@link EventProcessor} implementation.
         *
         * @param name A {@link String} defining this {@link EventProcessor} implementation.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder name(String name) {
            assertNonEmpty(name, "A name should be provided");
            this.name = name;
            return this;
        }

        /**
         * Sets the given {@code tenantSegmentFactory} to be used to construct tenant-specific {@link EventProcessor}
         * segments.
         *
         * @param tenantSegmentFactory The {@link TenantEventProcessorSegmentFactory} used to construct tenant-specific
         *                             {@link EventProcessor} segments.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder tenantSegmentFactory(TenantEventProcessorSegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory should not be null");
            this.tenantEventProcessorSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantEventProcessor} as specified through this Builder.
         *
         * @return a {@link MultiTenantEventProcessor} as specified through this Builder
         */
        public MultiTenantEventProcessor build() {
            return new MultiTenantEventProcessor(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *          *                                    specifications.
         */
        protected void validate() {
            assertNonEmpty(name, "The name is a hard requirement and should be provided");
            assertNonNull(tenantEventProcessorSegmentFactory,
                          "The TenantEventProcessorSegmentFactory is a hard requirement and should be provided");
        }
    }
}
