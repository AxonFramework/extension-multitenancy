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
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericStreamingQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class MultiTenantQueryBusTest {

    private MultiTenantQueryBus testSubject;
    private SimpleQueryBus fixtureSegment1;
    private SimpleQueryBus fixtureSegment2;

    @BeforeEach
    void setUp() {
        fixtureSegment1 = mock(SimpleQueryBus.class);
        fixtureSegment2 = mock(SimpleQueryBus.class);

        TenantQuerySegmentFactory tenantQuerySegmentFactory = t -> {
            if (t.tenantId().equals("fixtureTenant1")) {
                return fixtureSegment1;
            } else {
                return fixtureSegment2;
            }
        };
        TargetTenantResolver<QueryMessage<?, ?>> targetTenantResolver = (m, tenants) -> TenantDescriptor.tenantWithId(
                "fixtureTenant2");

        testSubject = MultiTenantQueryBus.builder()
                                         .tenantSegmentFactory(tenantQuerySegmentFactory)
                                         .targetTenantResolver(targetTenantResolver)
                                         .build();
    }

    @Test
    public void query() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        QueryMessage<String, String> query = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));
        testSubject.query(query);
        verify(fixtureSegment2).query(query);
        verify(fixtureSegment1, times(0)).query(any());
    }

    @Test
    public void scatterGather() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        QueryMessage<String, String> query = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));
        testSubject.scatterGather(query, 1000, TimeUnit.MILLISECONDS);
        verify(fixtureSegment2).scatterGather(query, 1000, TimeUnit.MILLISECONDS);
        verify(fixtureSegment1, times(0)).scatterGather(any(), anyLong(), any());
    }

    @Test
    public void streamingQuery() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        StreamingQueryMessage<String, String> query = new GenericStreamingQueryMessage<>("Hello, World", String.class);
        testSubject.streamingQuery(query);
        verify(fixtureSegment2).streamingQuery(query);
        verify(fixtureSegment1, times(0)).streamingQuery(any());
    }

    @Test
    public void subscriptionQuery() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "TEST_PAYLOAD",
                "queryName",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );
        testSubject.subscriptionQuery(queryMessage);
        testSubject.subscriptionQuery(queryMessage, 1);
        verify(fixtureSegment2).subscriptionQuery(queryMessage);
        verify(fixtureSegment2).subscriptionQuery(queryMessage, 1);
        verify(fixtureSegment1, times(0)).subscriptionQuery(any());
        verify(fixtureSegment1, times(0)).subscriptionQuery(any(), anyInt());
    }

    @Test
    public void registerTenantAfterCommandBusHaveBeenStarted() {
        when(fixtureSegment1.subscribe(anyString(), any(), any())).thenReturn(() -> true);
        when(fixtureSegment2.subscribe(anyString(), any(), any())).thenReturn(() -> true);

        MessageHandlerInterceptor<QueryMessage<?, ?>> messageHandlerInterceptor = (m, chain) -> chain.proceed();
        testSubject.registerHandlerInterceptor(messageHandlerInterceptor);

        MessageDispatchInterceptor<QueryMessage<?, ?>> messageDispatchInterceptor = messages -> (a, b) -> b;
        testSubject.registerDispatchInterceptor(messageDispatchInterceptor);

        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));

        MessageHandler<QueryMessage<?, ?>> queryHandler = q -> null;
        testSubject.subscribe("query", String.class, queryHandler);

        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        QueryMessage<String, String> query = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));
        testSubject.query(query);
        verify(fixtureSegment2).query(query);
        verify(fixtureSegment1, times(0)).query(any());

        verify(fixtureSegment1).subscribe("query", String.class, queryHandler);
        verify(fixtureSegment2).registerHandlerInterceptor(messageHandlerInterceptor);

        verify(fixtureSegment1).subscribe(any(), any(), any());
        verify(fixtureSegment2).registerDispatchInterceptor(messageDispatchInterceptor);
    }

    @Test
    public void unregister() {
        NoSuchTenantException noSuchTenantException = assertThrows(NoSuchTenantException.class, () -> {
            QueryMessage<String, String> query = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));
            testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2")).cancel();
            testSubject.query(query);
        });
        assertEquals("Unknown tenant: fixtureTenant2", noSuchTenantException.getMessage());
    }


    @Test
    public void registerDispatchInterceptor() {
        when(fixtureSegment2.registerDispatchInterceptor(any())).thenReturn(() -> true);
        MessageDispatchInterceptor<QueryMessage<?, ?>> messageDispatchInterceptor = messages -> (a, b) -> b;
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        Registration registration = testSubject.registerDispatchInterceptor(messageDispatchInterceptor);

        assertTrue(registration.cancel());
    }

    @Test
    public void registerHandlerInterceptor() {
        when(fixtureSegment2.registerHandlerInterceptor(any())).thenReturn(() -> true);
        MessageHandlerInterceptor<QueryMessage<?, ?>> messageHandlerInterceptor = (m, chain) -> chain.proceed();
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        Registration registration = testSubject.registerHandlerInterceptor(messageHandlerInterceptor);

        assertTrue(registration.cancel());
    }
}