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

package org.axonframework.extensions.multitenancy.components.scheduling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Multi-tenant implementation of {@link EventScheduler} that delegates to a tenant-specific {@link EventScheduler}
 * based on the {@link TenantDescriptor} resolved by the {@link TargetTenantResolver}.
 * <p>
 * Compared to other {@code EventScheduler} implementations, this version requires <em>any</em> given {@code event} for
 * {@link #schedule(Instant, Object) schedule} and {@link #reschedule(ScheduleToken, Instant, Object) reschedule} to be
 * of type {@link EventMessage}. Furthermore, the event message should contain {@link MetaData} with a registered
 * {@link TenantDescriptor}, as without the {@code TenantDescriptor} this scheduler is incapable of finding the
 * tenant-specific {@code EventScheduler} to invoke the task on.
 *
 * @author Stefan Dragisic
 * @since 4.9.0
 */
public class MultiTenantEventScheduler implements EventScheduler, MultiTenantAwareComponent {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final TenantEventSchedulerSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<EventMessage<?>> targetTenantResolver;

    private final Map<TenantDescriptor, EventScheduler> tenantSegments = new ConcurrentHashMap<>();

    /**
     * Instantiate a {@link MultiTenantEventScheduler} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MultiTenantEventScheduler} instance
     */
    protected MultiTenantEventScheduler(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MultiTenantEventScheduler}.
     * <p>
     * The {@link TenantEventSchedulerSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and
     * as such should be provided.
     *
     * @return a Builder to be able to create a {@link MultiTenantEventScheduler}
     */
    public static MultiTenantEventScheduler.Builder builder() {
        return new MultiTenantEventScheduler.Builder();
    }

    /**
     * Schedule the given {@code event} for publication at the given {@code triggerDateTime}.
     * </p>
     * It is <em>required</em> that the given {@code event} is of type {@link EventMessage}, containing a resolvable
     * {@link TenantDescriptor} from the {@link Message#getMetaData meta data}. Without a {@code TenantDescriptor}, the
     * `MultiTenantEventScheduler` is incapable of resolving the tenant-specific {@link EventScheduler}. Therefor, the
     * provided {@code event} should be of type {@code EventMessage} <em>with</em> a {@code TenantDescriptor} in it's
     * {@link MetaData}.
     * <p>
     * Convenience method around {@link #cancelSchedule(ScheduleToken)} and {@link #schedule(Duration, Object)}.
     *
     * @param instant The moment to trigger publication of the event.
     * @param event   The event to publish.
     * @return The token to use when canceling the schedule.
     */
    @Override
    public ScheduleToken schedule(Instant instant, Object event) {
        return resolveTenant(event).schedule(instant, event);
    }

    /**
     * Schedule the given {@code event} for publication after the given {@code duration}.
     * <p>
     * It is <em>required</em> that the given {@code event} is of type {@link EventMessage}, containing a resolvable
     * {@link TenantDescriptor} from the {@link Message#getMetaData meta data}. Without a {@code TenantDescriptor}, the
     * `MultiTenantEventScheduler` is incapable of resolving the tenant-specific {@link EventScheduler}. Therefor, the
     * provided {@code event} should be of type {@code EventMessage} <em>with</em> a {@code TenantDescriptor} in it's
     * {@link MetaData}.
     *
     * @param duration The amount of time to wait before publishing the event.
     * @param event    The event to publish.
     * @return The token to use when cancelling the schedule.
     */
    @Override
    public ScheduleToken schedule(Duration duration, Object event) {
        return resolveTenant(event).schedule(duration, event);
    }

    /**
     * Cancel the publication of a scheduled event. Tries to extract {@link TenantDescriptor} from
     * {@link TenantWrappedTransactionManager#getCurrentTenant()}.
     * <p>
     * If the {@link TenantDescriptor} is not found, it tries to cancel the schedule token in all tenants until it finds
     * the correct one. See {@link #forTenant(TenantDescriptor)}.
     *
     * @param scheduleToken The token returned when the event was scheduled, used to cancel the schedule.
     */
    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            tenantSegments.get(currentTenant).cancelSchedule(scheduleToken);
        } else {
            logger.info("No current tenant found. Canceling schedule token {} by searching in all tenants.",
                        scheduleToken);
            tenantSegments.forEach((tenantDescriptor, eventScheduler) -> {
                try {
                    logger.info("Cancelling schedule token {} for tenant {}.",
                                scheduleToken,
                                tenantDescriptor.tenantId());
                    eventScheduler.cancelSchedule(scheduleToken);
                } catch (IllegalArgumentException e) {
                    logger.info("Schedule token {} does not belong to tenant {}. Skipping cancel task for this tenant.",
                                scheduleToken,
                                tenantDescriptor.tenantId());
                }
            });
        }
    }

    /**
     * Cancel a scheduled event and schedule another in its place.
     * <p>
     * It is <em>required</em> that the given {@code event} is of type {@link EventMessage}, containing a resolvable
     * {@link TenantDescriptor} from the {@link Message#getMetaData meta data}. Without a {@code TenantDescriptor}, the
     * `MultiTenantEventScheduler` is incapable of resolving the tenant-specific {@link EventScheduler}. Therefor, the
     * provided {@code event} should be of type {@code EventMessage} <em>with</em> a {@code TenantDescriptor} in it's
     * {@link MetaData}.
     *
     * @param scheduleToken   The token returned when the event was scheduled, might be null.
     * @param triggerDuration The amount of time to wait before publishing the event.
     * @param event           The event to publish.
     * @return The token to use when cancelling the schedule.
     */
    @Override
    public ScheduleToken reschedule(ScheduleToken scheduleToken, Duration triggerDuration, Object event) {
        return resolveTenant(event).reschedule(scheduleToken, triggerDuration, event);
    }

    /**
     * Cancel a scheduled event and schedule another in its place.
     * <p>
     * It is <em>required</em> that the given {@code event} is of type {@link EventMessage}, containing a resolvable
     * {@link TenantDescriptor} from the {@link Message#getMetaData meta data}. Without a {@code TenantDescriptor}, the
     * `MultiTenantEventScheduler` is incapable of resolving the tenant-specific {@link EventScheduler}. Therefor, the
     * provided {@code event} should be of type {@code EventMessage} <em>with</em> a {@code TenantDescriptor} in it's
     * {@link MetaData}.
     *
     * @param scheduleToken The token returned when the event was scheduled, might be null.
     * @param instant       The moment in time to wait before publishing the event.
     * @param event         The event to publish.
     * @return The token to use when cancelling the schedule.
     */
    @Override
    public ScheduleToken reschedule(ScheduleToken scheduleToken, Instant instant, Object event) {
        return resolveTenant(event).reschedule(scheduleToken, instant, event);
    }

    /**
     * Shut down the scheduler, preventing it from scheduling any more tasks.
     * <p>
     * Invoking shutdown, shuts down all tenant-specific {@link EventScheduler EventSchedulers}.
     */
    @Override
    public void shutdown() {
        tenantSegments.forEach((tenantDescriptor, eventScheduler) -> eventScheduler.shutdown());
    }

    /**
     * Get the tenant-specific {@link EventScheduler} for given the {@link TenantDescriptor}.
     *
     * @param tenantDescriptor The tenant descriptor to retrieve the {@link EventScheduler} segment for.
     * @return The tenant-specific {@link EventScheduler} for the given {@code tenantDescriptor}. May return
     * {@code null} if the tenant wasn't {@link #registerTenant(TenantDescriptor) registered}.
     */
    public EventScheduler forTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor);
    }

    /**
     * The collection of all tenant-specific {@link EventScheduler EventSchedulers}
     * {@link #registerTenant(TenantDescriptor) registered}.
     *
     * @return The collection of all tenant-specific {@link EventScheduler EventSchedulers}
     * {@link #registerTenant(TenantDescriptor) registered}.
     */
    public Map<TenantDescriptor, EventScheduler> getTenantSegments() {
        return tenantSegments;
    }

    /**
     * Register a tenant specific event scheduler for given {@link TenantDescriptor}.
     *
     * @param tenantDescriptor The tenant to register an {@link EventScheduler} for.
     * @return The {@link Registration} of the tenant segment, to unregister when necessary.
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

    private EventScheduler unregisterTenant(TenantDescriptor tenantDescriptor) {
        EventScheduler eventScheduler = tenantSegments.remove(tenantDescriptor);
        if (eventScheduler != null) {
            eventScheduler.shutdown();
        }
        return eventScheduler;
    }

    /**
     * Register a tenant specific event scheduler for given {@link TenantDescriptor} and start it.
     *
     * @param tenantDescriptor The tenant to register an {@link EventScheduler} for.
     * @return The {@link Registration} of the tenant segment, to unregister when necessary.
     */
    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }

    private EventScheduler resolveTenant(Object event) {
        if (event instanceof EventMessage) {
            TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(
                    (EventMessage<?>) event, tenantSegments.keySet()
            );
            EventScheduler tenantSegment = tenantSegments.get(tenantDescriptor);
            if (tenantSegment == null) {
                throw new NoSuchTenantException(tenantDescriptor.tenantId());
            }
            return tenantSegment;
        } else {
            throw new IllegalArgumentException(
                    "Message is not an instance of EventMessage and doesn't contain Meta Data to resolve the tenant."
            );
        }
    }

    /**
     * Builder class to instantiate a {@link MultiTenantEventScheduler}.
     * <p>
     * The {@link TenantEventSchedulerSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and
     * as such should be provided.
     */
    public static class Builder {

        private TenantEventSchedulerSegmentFactory tenantSegmentFactory;
        private TargetTenantResolver<EventMessage<?>> targetTenantResolver;

        /**
         * Sets the {@link TenantEventSchedulerSegmentFactory} used to build {@link EventScheduler} segment for given
         * {@link TenantDescriptor}.
         *
         * @param tenantSegmentFactory The tenant aware segment factory used to build {@link EventScheduler} instances
         *                             per tenant.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder tenantSegmentFactory(TenantEventSchedulerSegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve a tenant {@link EventScheduler}segment based on an
         * {@link EventMessage}.
         *
         * @param targetTenantResolver The {@link TargetTenantResolver} used to resolve a tenant
         *                             {@link EventScheduler}segment based on an {@link EventMessage}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder targetTenantResolver(TargetTenantResolver<EventMessage<?>> targetTenantResolver) {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantEventScheduler} as specified through this Builder.
         *
         * @return A {@link MultiTenantEventScheduler} as specified through this Builder.
         */
        public MultiTenantEventScheduler build() {
            return new MultiTenantEventScheduler(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
        }
    }
}
