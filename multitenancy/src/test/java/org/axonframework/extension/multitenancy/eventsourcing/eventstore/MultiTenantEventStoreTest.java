/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.extension.multitenancy.eventsourcing.eventstore;

import org.axonframework.common.Registration;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantEventStore}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantEventStoreTest {

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private EventStore tenantSegment1;
    private EventStore tenantSegment2;

    private MultiTenantEventStore testSubject;

    @BeforeEach
    void setUp() {
        tenantSegment1 = mock(EventStore.class);
        tenantSegment2 = mock(EventStore.class);

        when(tenantSegment1.subscribe(any())).thenReturn(() -> true);
        when(tenantSegment2.subscribe(any())).thenReturn(() -> true);

        TenantEventSegmentFactory tenantSegmentFactory = tenant -> {
            if (tenant.tenantId().equals("tenant1")) {
                return tenantSegment1;
            } else {
                return tenantSegment2;
            }
        };

        // Resolver always resolves to tenant2
        TargetTenantResolver<Message> targetTenantResolver =
                (message, tenants) -> TENANT_2;

        testSubject = MultiTenantEventStore.builder()
                                           .tenantSegmentFactory(tenantSegmentFactory)
                                           .targetTenantResolver(targetTenantResolver)
                                           .build();
    }

    @Test
    void publishRoutesToCorrectTenant() {
        when(tenantSegment2.publish(any(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));

        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        EventMessage event = new GenericEventMessage(new MessageType("TestEvent"), "payload");
        CompletableFuture<Void> result = testSubject.publish(null, List.of(event));

        assertTrue(result.isDone());
        verify(tenantSegment2).publish(isNull(), eq(List.of(event)));
        verify(tenantSegment1, never()).publish(any(), anyList());
    }

    @Test
    void publishEmptyListReturnsImmediately() {
        testSubject.registerTenant(TENANT_1);

        CompletableFuture<Void> result = testSubject.publish(null, List.of());

        assertTrue(result.isDone());
        verify(tenantSegment1, never()).publish(any(), anyList());
        verify(tenantSegment2, never()).publish(any(), anyList());
    }

    @Test
    void publishToUnknownTenantThrowsException() {
        // No tenants registered
        EventMessage event = new GenericEventMessage(new MessageType("TestEvent"), "payload");

        assertThrows(NoSuchTenantException.class,
                     () -> testSubject.publish(null, List.of(event)));
    }

    @Test
    void subscribeRegistersOnExistingTenants() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        testSubject.subscribe((events, ctx) -> CompletableFuture.completedFuture(null));

        verify(tenantSegment1).subscribe(any());
        verify(tenantSegment2).subscribe(any());
    }

    @Test
    void registerAndStartTenantSubscribesExistingConsumers() {
        // First subscribe a consumer
        testSubject.subscribe((events, ctx) -> CompletableFuture.completedFuture(null));

        // Then register and start a tenant
        testSubject.registerAndStartTenant(TENANT_1);

        // Consumer should be subscribed to the new tenant segment
        verify(tenantSegment1).subscribe(any());
    }

    @Test
    void openStreamThrowsUnsupportedOperationException() {
        testSubject.registerTenant(TENANT_1);

        // Use the factory method to create a StreamingCondition
        StreamingCondition condition = StreamingCondition.startingFrom(null);
        assertThrows(UnsupportedOperationException.class,
                     () -> testSubject.open(condition, null));
    }

    @Test
    void firstTokenThrowsUnsupportedOperationException() {
        testSubject.registerTenant(TENANT_1);

        assertThrows(UnsupportedOperationException.class,
                     () -> testSubject.firstToken(null));
    }

    @Test
    void latestTokenThrowsUnsupportedOperationException() {
        testSubject.registerTenant(TENANT_1);

        assertThrows(UnsupportedOperationException.class,
                     () -> testSubject.latestToken(null));
    }

    @Test
    void tokenAtThrowsUnsupportedOperationException() {
        testSubject.registerTenant(TENANT_1);

        assertThrows(UnsupportedOperationException.class,
                     () -> testSubject.tokenAt(java.time.Instant.now(), null));
    }

    @Test
    void unregisterTenantRemovesTenantFromRouting() {
        Registration registration = testSubject.registerTenant(TENANT_2);

        // Unregister the tenant
        registration.cancel();

        // Should throw because tenant is no longer registered
        EventMessage event = new GenericEventMessage(new MessageType("TestEvent"), "payload");
        assertThrows(NoSuchTenantException.class,
                     () -> testSubject.publish(null, List.of(event)));
    }

    @Test
    void tenantSegmentsReturnsRegisteredTenants() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        assertEquals(2, testSubject.tenantSegments().size());
        assertTrue(testSubject.tenantSegments().containsKey(TENANT_1));
        assertTrue(testSubject.tenantSegments().containsKey(TENANT_2));
    }

    @Test
    void builderRequiresTenantSegmentFactory() {
        assertThrows(Exception.class, () ->
                MultiTenantEventStore.builder()
                                     .targetTenantResolver((m, t) -> TENANT_1)
                                     .build()
        );
    }

    @Test
    void builderRequiresTargetTenantResolver() {
        assertThrows(Exception.class, () ->
                MultiTenantEventStore.builder()
                                     .tenantSegmentFactory(t -> tenantSegment1)
                                     .build()
        );
    }
}
