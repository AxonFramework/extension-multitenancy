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

package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;
import org.junit.jupiter.api.*;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class MultiTenantQueryUpdateEmitterTest {

    private MultiTenantQueryUpdateEmitter testSubject;
    private QueryUpdateEmitter fixtureSegment1;
    private QueryUpdateEmitter fixtureSegment2;

    @BeforeEach
    void setUp() {
        fixtureSegment1 = mock(QueryUpdateEmitter.class);
        fixtureSegment2 = mock(QueryUpdateEmitter.class);

        TenantQueryUpdateEmitterSegmentFactory tenantQueryUpdateEmitterSegmentFactory = t -> {
            if (t.tenantId().equals("fixtureTenant1")) {
                return fixtureSegment1;
            } else {
                return fixtureSegment2;
            }
        };
        TargetTenantResolver<Message<?>> targetTenantResolver = (m, tenants) -> TenantDescriptor.tenantWithId(
                "fixtureTenant2");

        testSubject = MultiTenantQueryUpdateEmitter.builder()
                                                   .tenantSegmentFactory(tenantQueryUpdateEmitterSegmentFactory)
                                                   .targetTenantResolver(targetTenantResolver)
                                                   .build();
    }

    @Test
    public void emit() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        Predicate<SubscriptionQueryMessage<?, ?, SubscriptionQueryUpdateMessage<?>>> filter =
                m -> true;

        Predicate<? super String> predicate =
                p -> true;

        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage("TEST_QUERY_UPDATE");
        testSubject.emit(filter, testSubscriptionQueryUpdateMessage);

        verify(fixtureSegment2).emit(filter, testSubscriptionQueryUpdateMessage);
        verify(fixtureSegment1, times(0)).emit(filter, testSubscriptionQueryUpdateMessage);

        UnitOfWork<SubscriptionQueryUpdateMessage<Object>> mockUnitOfWork = mock(UnitOfWork.class);
        when(mockUnitOfWork.getMessage()).thenReturn(testSubscriptionQueryUpdateMessage);
        CurrentUnitOfWork.set(mockUnitOfWork);

        testSubject.emit(String.class, predicate, "update");
        verify(fixtureSegment2).emit(String.class, predicate, "update");
        verify(fixtureSegment1, times(0)).emit(String.class, predicate, "update");

        testSubject.emit(String.class, predicate, testSubscriptionQueryUpdateMessage);
        verify(fixtureSegment2).emit(String.class, predicate, testSubscriptionQueryUpdateMessage);
        verify(fixtureSegment1, times(0)).emit(String.class, predicate, testSubscriptionQueryUpdateMessage);

        testSubject.emit(m -> true, "update");
        verify(fixtureSegment2).emit(any(), eq("update"));
        verify(fixtureSegment1, times(0)).emit(any(), anyString());
    }

    @Test
    public void registerUpdateHandler() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        UpdateHandlerRegistration mockRegistration = mock(UpdateHandlerRegistration.class);
        doNothing().when(mockRegistration).complete();
        when(fixtureSegment1.queryUpdateHandlerRegistered(any())).thenReturn(true);

        when(fixtureSegment2.registerUpdateHandler(any(), anyInt())).thenReturn(mockRegistration);

        SubscriptionQueryMessage<String, String, String> testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>("TEST_QUERY",
                                                      ResponseTypes.instanceOf(String.class),
                                                      ResponseTypes.instanceOf(String.class));

        UpdateHandlerRegistration<Object> updateHandlerRegistration = testSubject.registerUpdateHandler(
                testSubscriptionQueryMessage,
                10);

        assertTrue(testSubject.queryUpdateHandlerRegistered(testSubscriptionQueryMessage));
        verify(fixtureSegment2).registerUpdateHandler(testSubscriptionQueryMessage, 10);
        verify(fixtureSegment1, times(0)).registerUpdateHandler(any(), anyInt());

        updateHandlerRegistration.complete();
        verify(mockRegistration, times(1)).complete();
    }


    @Test
    public void unknownTenant() {
        SubscriptionQueryMessage<?, ?, ?> msg = mock(SubscriptionQueryMessage.class);

        NoSuchTenantException noSuchTenantException = assertThrows(NoSuchTenantException.class, () -> {
            testSubject.emit(String.class, p -> true, msg);
        });
        assertEquals("Unknown tenant: fixtureTenant2", noSuchTenantException.getMessage());
    }


    @Test
    public void unregister() {
        SubscriptionQueryMessage<?, ?, ?> msg = mock(SubscriptionQueryMessage.class);
        NoSuchTenantException noSuchTenantException = assertThrows(NoSuchTenantException.class, () -> {
            testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2")).cancel();
            testSubject.emit(String.class, p -> true, msg);
        });
        assertEquals("Unknown tenant: fixtureTenant2", noSuchTenantException.getMessage());
    }

    @Test
    public void getTenant() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        QueryUpdateEmitter tenant = testSubject.getTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        assertEquals(fixtureSegment1, tenant);
    }

    @Test
    public void registerDispatchInterceptor() {
        when(fixtureSegment2.registerDispatchInterceptor(any())).thenReturn(() -> true);
        MessageDispatchInterceptor<Message<?>> messageDispatchInterceptor = messages -> (a, b) -> b;
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        Registration registration = testSubject.registerDispatchInterceptor(messageDispatchInterceptor);

        assertTrue(registration.cancel());
    }

    @Test
    public void unregisterTenant() {
        when(fixtureSegment2.registerDispatchInterceptor(any())).thenReturn(() -> true);
        MessageDispatchInterceptor<Message<?>> messageDispatchInterceptor = messages -> (a, b) -> b;
        Registration registration = testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        testSubject.registerDispatchInterceptor(messageDispatchInterceptor);

        assertTrue(registration.cancel());
    }

    @Test
    public void completeUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> {
            testSubject.complete(p -> true);
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            testSubject.complete(String.class, p -> true);
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            testSubject.completeExceptionally(p -> true, new RuntimeException());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            testSubject.completeExceptionally(String.class, p -> true, new RuntimeException());
        });
    }
}