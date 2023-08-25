/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSchedulerSegmentFactory;
import org.axonframework.messaging.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Multi-tenant implementation of {@link EventScheduler} that delegates to a tenant-specific {@link EventScheduler}
 * based on the {@link TenantDescriptor} resolved by the {@link TargetTenantResolver}.
 *
 * @author Stefan Dragisic
 * @since 4.9.0
 */
public class MultiTenantEventScheduler implements EventScheduler, MultiTenantAwareComponent {

    private final TenantEventSchedulerSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<EventMessage<?>> targetTenantResolver;

    private final Map<TenantDescriptor, EventScheduler> tenantSegments = new ConcurrentHashMap<>();

    /**
     * Instantiate a {@link MultiTenantEventScheduler} based on the fields contained in the {@link Builder}.
     * @param builder the {@link Builder} used to instantiate a {@link MultiTenantEventScheduler} instance
     */
    public MultiTenantEventScheduler(MultiTenantEventScheduler.Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MultiTenantEventScheduler}.
     * @return a Builder to be able to create a {@link MultiTenantEventScheduler}
     */
    public static MultiTenantEventScheduler.Builder builder() {
        return new MultiTenantEventScheduler.Builder();
    }

    /**
     * Schedule the given {@code event} for publication at the given {@code triggerDateTime}.
     * Message must contain MetaData with {@link TenantDescriptor} to resolve the tenant.
     * Therefor only EventMessage type is supported.
     *
     * @param instant The moment to trigger publication of the event
     * @param o           The event to publish
     * @return the token to use when cancelling the schedule
     */
    @Override
    public ScheduleToken schedule(Instant instant, Object o) {
        return resolveTenant(o).schedule(instant, o);
    }

    /**
     * Schedule the given {@code event} for publication after the given {@code duration}.
     * Message must contain MetaData with {@link TenantDescriptor} to resolve the tenant.
     * Therefor only EventMessage type is supported.
     *
     * @param duration The amount of time to wait before publishing the event
     * @param o           The event to publish
     * @return the token to use when cancelling the schedule
     */
    @Override
    public ScheduleToken schedule(Duration duration, Object o) {
        return resolveTenant(o).schedule(duration, o);
    }

    /**
     * Cancel the publication of a scheduled event. This method is not supported for multi-tenant event scheduler.
     * Get the tenant specific event scheduler and cancel the schedule there.
     * See {@link #forTenant(TenantDescriptor)}.
     *
     * @param scheduleToken the token returned when the event was scheduled
     */
    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        throw new UnsupportedOperationException("Cancel schedule is not supported for multi-tenant event scheduler directly."
                                                        + "Get the tenant specific event scheduler and cancel the schedule there.");
    }

    /**
     * Cancel a scheduled event and schedule another in its place.
     * @param scheduleToken   the token returned when the event was scheduled, might be null
     * @param triggerDuration The amount of time to wait before publishing the event
     * @param event           The event to publish
     * @return the token to use when cancelling the schedule
     */
    @Override
    public ScheduleToken reschedule(ScheduleToken scheduleToken, Duration triggerDuration, Object event) {
       return resolveTenant(event).reschedule(scheduleToken, triggerDuration, event);
    }

    /**
     * Cancel a scheduled event and schedule another in its place. Message must contain MetaData with {@link
     * TenantDescriptor} to resolve the tenant. Therefor only EventMessage type is supported.
     *
     * @param scheduleToken   the token returned when the event was scheduled, might be null
     * @param instant The moment in time to wait before publishing the event
     * @param event           The event to publish
     * @return the token to use when cancelling the schedule
     */
    @Override
    public ScheduleToken reschedule(ScheduleToken scheduleToken, Instant instant, Object event) {
        return resolveTenant(event).reschedule(scheduleToken, instant, event);
    }

    /**
     * Shut down the scheduler, preventing it from scheduling any more tasks.
     * Invoking shutdown, shuts down all tenant specific event schedulers.
     */
    @Override
    public void shutdown() {
        tenantSegments.forEach((tenantDescriptor, eventScheduler) -> eventScheduler.shutdown());
    }

    /**
     * Get the tenant specific event scheduler for given {@link TenantDescriptor}.
     * @param tenantDescriptor tenant descriptor
     * @return tenant specific event scheduler
     */
    public EventScheduler forTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor);
    }

    /**
     * Get the map of tenant specific event schedulers.
     * @return map of tenant specific event schedulers
     */
    public Map<TenantDescriptor, EventScheduler> getTenantSegments() {
        return tenantSegments;
    }

    /**
     * Register a tenant specific event scheduler for given {@link TenantDescriptor}.
     * @param tenantDescriptor for the component to register
     * @return registration to unregister the component
     */
    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        EventScheduler tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);


        return () -> {
            EventScheduler delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    /**
     * Unregister a tenant specific event scheduler for given {@link TenantDescriptor}.
     * @param tenantDescriptor
     * @return the unregistered tenant specific event scheduler
     */
    private EventScheduler unregisterTenant(TenantDescriptor tenantDescriptor) {
        EventScheduler eventScheduler = tenantSegments.remove(tenantDescriptor);
        eventScheduler.shutdown();
        return eventScheduler;
    }

    /**
     * Register a tenant specific event scheduler for given {@link TenantDescriptor} and start it.
     * @param tenantDescriptor for the component to register
     * @return registration to unregister the component
     */
    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }

    private EventScheduler resolveTenant(Object eventMessage) {
        if (eventMessage instanceof EventMessage) {
            TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant((EventMessage<?>) eventMessage, tenantSegments.keySet());
            EventScheduler tenantSegment = tenantSegments.get(tenantDescriptor);
            if (tenantSegment == null) {
                throw new NoSuchTenantException(tenantDescriptor.tenantId());
            }
            return tenantSegment;
        } else {
            throw new IllegalArgumentException("Message is not an instance of EventMessage and doesn't contain Meta Data to resolve the tenant.");
        }
    }

    /**
     * Builder class to instantiate a {@link MultiTenantEventScheduler}.
     * <p>
     * The {@link TenantEventSchedulerSegmentFactory} is a <b>hard requirement</b> and as such should be provided.
     * The {@link TargetTenantResolver} is a <b>hard requirement</b> and as such should be provided.
     * The {@link TenantEventSchedulerSegmentFactory} is used to build {@link EventScheduler} segment for given {@link
     * TenantDescriptor}.
     * The {@link TargetTenantResolver} is used to resolve correct tenant segment based on {@link Message} message.
     */
    public static class Builder {

        public TenantEventSchedulerSegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<EventMessage<?>> targetTenantResolver;

        /**
         * Sets the {@link MultiTenantEventScheduler} used to build {@link EventScheduler} segment for given {@link
         * TenantDescriptor}.
         *
         * @param tenantSegmentFactory tenant aware segment factory
         * @return the current Builder instance, for fluent interfacing
         */
        public MultiTenantEventScheduler.Builder tenantSegmentFactory(TenantEventSchedulerSegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory,
                                       "The TenantEventProcessorSegmentFactory is a hard requirement");
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
        public MultiTenantEventScheduler.Builder targetTenantResolver(TargetTenantResolver<EventMessage<?>> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantEventScheduler} as specified through this Builder.
         * @return a {@link MultiTenantEventScheduler} as specified through this Builder
         */
        public MultiTenantEventScheduler build() {
            return new MultiTenantEventScheduler(this);
        }

        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
        }
    }
}
