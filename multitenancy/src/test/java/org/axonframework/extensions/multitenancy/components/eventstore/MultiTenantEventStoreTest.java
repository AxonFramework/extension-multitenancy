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

package org.axonframework.extensions.multitenancy.components.eventstore;

import org.axonframework.common.Registration;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.MultiSourceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantEventStore}.
 *
 * @author Stefan Dragisic
 */
@SuppressWarnings("resource")
class MultiTenantEventStoreTest {

    private EventStore fixtureSegment1;
    private EventStore fixtureSegment2;

    private MultiTenantEventStore testSubject;

    @BeforeEach
    void setUp() {
        fixtureSegment1 = mock(EventStore.class);
        fixtureSegment2 = mock(EventStore.class);

        TenantEventSegmentFactory tenantEventSegmentFactory = t -> {
            if (t.tenantId().equals("fixtureTenant1")) {
                return fixtureSegment1;
            } else {
                return fixtureSegment2;
            }
        };
        TargetTenantResolver<Message<?>> targetTenantResolver = (m, tenants) -> TenantDescriptor.tenantWithId(
                "fixtureTenant2");

        testSubject = MultiTenantEventStore.builder()
                                           .tenantSegmentFactory(tenantEventSegmentFactory)
                                           .targetTenantResolver(targetTenantResolver)
                                           .build();
    }

    @Test
    void publish() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<?> event = mock(EventMessage.class);
        testSubject.publish(event);
        verify(fixtureSegment2).publish(event);
        verify(fixtureSegment1, times(0)).publish(any(EventMessage.class));
    }

    @Test
    void publishMany() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<?> event1 = mock(EventMessage.class);
        EventMessage<?> event2 = mock(EventMessage.class);
        testSubject.publish(event1, event2);
        verify(fixtureSegment2).publish(event1, event2);
        verify(fixtureSegment1, times(0)).publish(any(), any());
    }

    @Test
    void publishList() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<?> event1 = mock(EventMessage.class);
        EventMessage<?> event2 = mock(EventMessage.class);
        List<EventMessage<?>> eventMessages = Arrays.asList(event1, event2);
        testSubject.publish(eventMessages);
        verify(fixtureSegment2).publish(eventMessages);
        verify(fixtureSegment1, times(0)).publish(eventMessages);
    }

    @Test
    void unknownTenant() {
        NoSuchTenantException noSuchTenantException = assertThrows(NoSuchTenantException.class, () -> {
            EventMessage<?> event = mock(EventMessage.class);
            testSubject.publish(event);
        });
        assertEquals("Tenant with identifier [fixtureTenant2] is unknown", noSuchTenantException.getMessage());
    }

    @Test
    void unregister() {
        NoSuchTenantException noSuchTenantException = assertThrows(NoSuchTenantException.class, () -> {
            EventMessage<?> event = mock(EventMessage.class);
            testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2")).cancel();
            testSubject.publish(event);
        });
        assertEquals("Tenant with identifier [fixtureTenant2] is unknown", noSuchTenantException.getMessage());
    }

    @Test
    void registerTenantAfterStoreHaveBeenStarted() {
        when(fixtureSegment1.subscribe(any())).thenReturn(() -> true);
        when(fixtureSegment2.subscribe(any())).thenReturn(() -> true);

        MessageDispatchInterceptor<EventMessage<?>> messageDispatchInterceptor = messages -> (a, b) -> b;
        testSubject.registerDispatchInterceptor(messageDispatchInterceptor);
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));

        Consumer<List<? extends EventMessage<?>>> consumer = e -> {
        };
        testSubject.subscribe(consumer);
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        EventMessage<?> event = mock(EventMessage.class);
        testSubject.publish(event);
        verify(fixtureSegment2).publish(event);
        verify(fixtureSegment1, times(0)).publish(any(EventMessage.class));
        verify(fixtureSegment1).subscribe(consumer);
        verify(fixtureSegment1).subscribe(consumer);
        verify(fixtureSegment2).registerDispatchInterceptor(messageDispatchInterceptor);
    }

    @Test
    void registerDispatchInterceptor() {
        when(fixtureSegment2.registerDispatchInterceptor(any())).thenReturn(() -> true);
        MessageDispatchInterceptor<EventMessage<?>> messageDispatchInterceptor = messages -> (a, b) -> b;

        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        Registration registration = testSubject.registerDispatchInterceptor(messageDispatchInterceptor);

        assertTrue(registration.cancel());
    }

    @Test
    void readEvents() {
        //noinspection rawtypes
        UnitOfWork unitOfWork = mock(UnitOfWork.class);
        EventMessage<?> eventMessage = mock(EventMessage.class);
        when(unitOfWork.getMessage()).thenReturn(eventMessage);
        when(unitOfWork.getCorrelationData()).thenReturn(MetaData.emptyInstance());
        CurrentUnitOfWork.set(unitOfWork);
        DomainEventStream domainEventStream = mock(DomainEventStream.class);
        when(fixtureSegment2.readEvents(anyString())).thenReturn(domainEventStream);
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        testSubject.readEvents("aggregateId");
        verify(fixtureSegment2).readEvents("aggregateId");
    }

    @Test
    void readEventsWithTenant() {
        DomainEventStream domainEventStream = mock(DomainEventStream.class);
        when(fixtureSegment2.readEvents(anyString())).thenReturn(domainEventStream);
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        testSubject.readEvents("aggregateId", TenantDescriptor.tenantWithId("fixtureTenant2"));
        verify(fixtureSegment2).readEvents("aggregateId");
    }

    @Test
    void storeSnapshot() {
        EventMessage<?> eventMessage = mock(EventMessage.class);
        DefaultUnitOfWork.startAndGet(eventMessage);
        DomainEventMessage<?> snapshot = mock(DomainEventMessage.class);
        doNothing().when(fixtureSegment2).storeSnapshot(any());
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        testSubject.storeSnapshot(snapshot);
        verify(fixtureSegment2).storeSnapshot(snapshot);
    }

    @Test
    void storeSnapshotWithTenant() {
        DomainEventMessage<?> snapshot = mock(DomainEventMessage.class);
        doNothing().when(fixtureSegment2).storeSnapshot(any());
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        testSubject.storeSnapshot(snapshot, TenantDescriptor.tenantWithId("fixtureTenant2"));
        verify(fixtureSegment2).storeSnapshot(snapshot);
    }

    @SuppressWarnings({"unchecked", "resource"})
    @Test
    void openStream() {
        BlockingStream<TrackedEventMessage<?>> mockStream1 = mock(BlockingStream.class);
        BlockingStream<TrackedEventMessage<?>> mockStream2 = mock(BlockingStream.class);
        when(fixtureSegment1.openStream(any())).thenReturn(mockStream1);
        when(fixtureSegment2.openStream(any())).thenReturn(mockStream2);
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        MultiSourceTrackingToken trackingToken = mock(MultiSourceTrackingToken.class);
        BlockingStream<TrackedEventMessage<?>> trackedEventMessageBlockingStream = testSubject.openStream(trackingToken);

        trackedEventMessageBlockingStream.peek();
        verify(mockStream1, times(1)).peek();
        verify(mockStream2, times(1)).peek();
    }
}