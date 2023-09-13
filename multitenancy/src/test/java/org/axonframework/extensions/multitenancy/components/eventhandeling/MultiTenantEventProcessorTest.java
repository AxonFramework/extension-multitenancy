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

package org.axonframework.extensions.multitenancy.components.eventhandeling;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantEventProcessor}.
 *
 * @author Stefan Dragisic
 */
@SuppressWarnings("resource")
class MultiTenantEventProcessorTest {

    private StreamingEventProcessor fixtureSegment1;
    private SubscribingEventProcessor fixtureSegment2;

    private MultiTenantEventProcessor testSubject;

    @BeforeEach
    void setUp() {
        fixtureSegment1 = mock(StreamingEventProcessor.class);
        fixtureSegment2 = mock(SubscribingEventProcessor.class);

        TenantEventProcessorSegmentFactory tenantEventProcessorSegmentFactory = t -> {
            if (t.tenantId().equals("fixtureTenant1")) {
                return fixtureSegment1;
            } else {
                return fixtureSegment2;
            }
        };

        testSubject = MultiTenantEventProcessor.builder()
                                               .name("testSubject")
                                               .tenantSegmentFactory(tenantEventProcessorSegmentFactory)
                                               .build();
    }

    @Test
    void registerTenant() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        assertTrue(testSubject.tenantEventProcessors().contains(fixtureSegment1));
    }

    @Test
    void registerAndStartTenant() {
        doNothing().when(fixtureSegment1).start();
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> interceptorChain.proceed());
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        assertTrue(testSubject.tenantEventProcessors().contains(fixtureSegment1));
        verify(fixtureSegment1, times(1)).start();
    }

    @Test
    void stopAndRemoveTenant() {
        doNothing().when(fixtureSegment1).shutDown();
        when(fixtureSegment1.registerHandlerInterceptor(any())).thenReturn(() -> true);
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> interceptorChain.proceed());
        testSubject.stopAndRemoveTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        assertTrue(testSubject.tenantEventProcessors().isEmpty());
        verify(fixtureSegment1, times(1)).shutDown();
    }

    @Test
    void registerHandlerInterceptor() {
        when(fixtureSegment1.registerHandlerInterceptor(any())).thenReturn(() -> true);
        MessageHandlerInterceptor<EventMessage<?>> messageHandlerInterceptor = (m, chain) -> chain.proceed();
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        Registration registration = testSubject.registerHandlerInterceptor(messageHandlerInterceptor);

        assertTrue(registration.cancel());
    }

    @Test
    void startAndShutDown() {
        when(fixtureSegment1.isRunning()).thenReturn(true);
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        assertTrue(testSubject.tenantEventProcessors().contains(fixtureSegment1));
        testSubject.start();
        assertTrue(testSubject.isRunning());
        assertTrue(testSubject.isRunning(TenantDescriptor.tenantWithId("fixtureTenant1")));
        verify(fixtureSegment1, times(1)).start();

        testSubject.shutDown();
        assertFalse(testSubject.isRunning());
        verify(fixtureSegment1, times(1)).shutDown();
    }
}