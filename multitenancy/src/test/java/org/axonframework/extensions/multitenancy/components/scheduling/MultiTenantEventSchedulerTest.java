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

import org.axonframework.common.Registration;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantEventScheduler}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantEventSchedulerTest {

    private MultiTenantEventScheduler testSubject;
    private EventScheduler fixtureSegment1;
    private EventScheduler fixtureSegment2;

    @BeforeEach
    void setUp() {
        fixtureSegment1 = mock(EventScheduler.class);
        fixtureSegment2 = mock(EventScheduler.class);

        TenantEventSchedulerSegmentFactory tenantSegmentFactory = t -> {
            if (t.tenantId().equals("fixtureTenant1")) {
                return fixtureSegment1;
            } else {
                return fixtureSegment2;
            }
        };
        TargetTenantResolver<EventMessage<?>> targetTenantResolver =
                (m, tenants) -> TenantDescriptor.tenantWithId("fixtureTenant2");

        testSubject = MultiTenantEventScheduler.builder()
                                               .tenantSegmentFactory(tenantSegmentFactory)
                                               .targetTenantResolver(targetTenantResolver)
                                               .build();
    }

    @Test
    void scheduleWithInstantAndTenantMetaData() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<Object> event = GenericEventMessage.asEventMessage("event");
        Instant instant = Instant.MAX;
        testSubject.schedule(instant, event);
        verify(fixtureSegment2).schedule(instant, event);
        verify(fixtureSegment1, times(0)).schedule(any(Instant.class), any());
    }


    @Test
    void scheduleWithDurationAndTenantMetaData() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<Object> event = GenericEventMessage.asEventMessage("event");
        Duration duration = Duration.ZERO;
        testSubject.schedule(duration, event);
        verify(fixtureSegment2).schedule(duration, event);
        verify(fixtureSegment1, times(0)).schedule(any(Duration.class), any());
    }

    @Test
    void scheduleNonEventMessageThrowsIllegalArgumentException() {
        assertThrowsExactly(IllegalArgumentException.class, () -> testSubject.schedule(Instant.MAX, "event"));
    }

    @Test
    void scheduleEventMessageWithoutTenantDescriptorInTheMetaDataThrowsNoSuchTenantException() {
        EventMessage<Object> event = GenericEventMessage.asEventMessage("event");

        assertThrowsExactly(NoSuchTenantException.class, () -> testSubject.schedule(Instant.MAX, event));
    }

    @Test
    void cancelScheduleWithTenantInfo() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        new TenantWrappedTransactionManager(
                NoTransactionManager.INSTANCE, TenantDescriptor.tenantWithId("fixtureTenant2")
        ).executeInTransaction(() -> testSubject.cancelSchedule(mock(ScheduleToken.class)));

        verify(fixtureSegment2).cancelSchedule(any());
        verifyNoInteractions(fixtureSegment1);
    }

    @Test
    void cancelScheduleNoTenantInfo() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        doThrow(IllegalArgumentException.class).when(fixtureSegment1).cancelSchedule(any());
        doNothing().when(fixtureSegment2).cancelSchedule(any());

        testSubject.cancelSchedule(mock(ScheduleToken.class));
        verify(fixtureSegment2).cancelSchedule(any());
        verify(fixtureSegment1).cancelSchedule(any());
    }

    @Test
    void cancelScheduleOnTenantSegment() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        TenantDescriptor fixtureTenant = TenantDescriptor.tenantWithId("fixtureTenant2");
        testSubject.forTenant(fixtureTenant).cancelSchedule(mock(ScheduleToken.class));
        verify(fixtureSegment2).cancelSchedule(any());
    }

    @Test
    void reschedule() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<Object> event = GenericEventMessage.asEventMessage("event");
        Instant instant = Instant.MIN;
        ScheduleToken scheduleToken = mock(ScheduleToken.class);
        testSubject.reschedule(scheduleToken, instant, event);
        verify(fixtureSegment2).reschedule(scheduleToken, instant, event);
        verify(fixtureSegment1, times(0)).reschedule(scheduleToken, instant, event);
    }

    @Test
    void rescheduleDuration() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<Object> event = GenericEventMessage.asEventMessage("event");
        Duration duration = Duration.ZERO;
        ScheduleToken scheduleToken = mock(ScheduleToken.class);
        testSubject.reschedule(scheduleToken, duration, event);
        verify(fixtureSegment2).reschedule(scheduleToken, duration, event);
        verify(fixtureSegment1, times(0)).reschedule(scheduleToken, duration, event);
    }

    @Test
    void shutdownAll() {
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        //noinspection resource
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        doNothing().when(fixtureSegment1).shutdown();
        doNothing().when(fixtureSegment2).shutdown();

        testSubject.shutdown();

        verify(fixtureSegment1).shutdown();
        verify(fixtureSegment2).shutdown();
    }

    @Test
    void registerUnregisterTenant() {
        TenantDescriptor tenantDescriptor = TenantDescriptor.tenantWithId("fixtureTenant1");
        //noinspection resource
        Registration registeredTenant = testSubject.registerTenant(tenantDescriptor);
        assertNotNull(testSubject.forTenant(tenantDescriptor));

        registeredTenant.cancel();
        assertNull(testSubject.forTenant(tenantDescriptor));
    }
}