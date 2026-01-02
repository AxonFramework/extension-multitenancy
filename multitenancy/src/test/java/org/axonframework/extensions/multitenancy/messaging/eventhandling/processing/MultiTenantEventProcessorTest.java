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

package org.axonframework.extensions.multitenancy.messaging.eventhandling.processing;

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantEventProcessor}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantEventProcessorTest {

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private EventProcessor tenantSegment1;
    private EventProcessor tenantSegment2;

    private MultiTenantEventProcessor testSubject;

    @BeforeEach
    void setUp() {
        tenantSegment1 = mock(EventProcessor.class);
        tenantSegment2 = mock(EventProcessor.class);

        when(tenantSegment1.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(tenantSegment2.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(tenantSegment1.shutdown()).thenReturn(CompletableFuture.completedFuture(null));
        when(tenantSegment2.shutdown()).thenReturn(CompletableFuture.completedFuture(null));

        TenantEventProcessorSegmentFactory tenantSegmentFactory = tenant -> {
            if (tenant.tenantId().equals("tenant1")) {
                return tenantSegment1;
            } else {
                return tenantSegment2;
            }
        };

        testSubject = MultiTenantEventProcessor.builder()
                                               .name("testProcessor")
                                               .tenantSegmentFactory(tenantSegmentFactory)
                                               .build();
    }

    @Test
    void nameReturnsProcessorName() {
        assertEquals("testProcessor", testSubject.name());
    }

    @Test
    void registerTenantAddsTenantSegment() {
        testSubject.registerTenant(TENANT_1);

        assertEquals(1, testSubject.tenantSegments().size());
        assertTrue(testSubject.tenantSegments().containsKey(TENANT_1));
        assertTrue(testSubject.tenantEventProcessors().contains(tenantSegment1));
    }

    @Test
    void registerTenantAfterStartThrowsException() {
        testSubject.registerTenant(TENANT_1);
        testSubject.start();

        assertThrows(IllegalStateException.class, () -> testSubject.registerTenant(TENANT_2));
    }

    @Test
    void registerAndStartTenantStartsSegment() {
        testSubject.registerAndStartTenant(TENANT_1);

        verify(tenantSegment1).start();
        assertTrue(testSubject.tenantEventProcessors().contains(tenantSegment1));
    }

    @Test
    void startStartsAllTenantSegments() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        CompletableFuture<Void> result = testSubject.start();

        assertTrue(result.isDone());
        verify(tenantSegment1).start();
        verify(tenantSegment2).start();
        assertTrue(testSubject.isRunning());
    }

    @Test
    void shutdownShutdownsAllTenantSegments() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);
        testSubject.start();

        CompletableFuture<Void> result = testSubject.shutdown();

        assertTrue(result.isDone());
        verify(tenantSegment1).shutdown();
        verify(tenantSegment2).shutdown();
        assertFalse(testSubject.isRunning());
    }

    @Test
    void isRunningForTenantDelegatesToTenantSegment() {
        when(tenantSegment1.isRunning()).thenReturn(true);
        testSubject.registerTenant(TENANT_1);

        assertTrue(testSubject.isRunning(TENANT_1));
        verify(tenantSegment1).isRunning();
    }

    @Test
    void isErrorForTenantDelegatesToTenantSegment() {
        when(tenantSegment1.isError()).thenReturn(true);
        testSubject.registerTenant(TENANT_1);

        assertTrue(testSubject.isError(TENANT_1));
        verify(tenantSegment1).isError();
    }

    @Test
    void isErrorReturnsFalseForMultiTenantProcessor() {
        // The multi-tenant processor itself never reports error
        // Individual tenant segments should be checked via isError(TenantDescriptor)
        assertFalse(testSubject.isError());
    }

    @Test
    void stopAndRemoveTenantShutdownsAndRemovesSegment() {
        testSubject.registerAndStartTenant(TENANT_1);

        boolean removed = testSubject.stopAndRemoveTenant(TENANT_1);

        assertTrue(removed);
        verify(tenantSegment1).shutdown();
        assertFalse(testSubject.tenantSegments().containsKey(TENANT_1));
        assertTrue(testSubject.tenantEventProcessors().isEmpty());
    }

    @Test
    void stopAndRemoveTenantReturnsFalseForUnknownTenant() {
        boolean removed = testSubject.stopAndRemoveTenant(TENANT_1);

        assertFalse(removed);
    }

    @Test
    void unregisterTenantViaCancelStopsAndRemoves() {
        testSubject.registerAndStartTenant(TENANT_1);

        testSubject.registerAndStartTenant(TENANT_1).cancel();

        // Since registerAndStartTenant returns a registration that calls stopAndRemoveTenant
        verify(tenantSegment1).shutdown();
    }

    @Test
    void tenantEventProcessorsReturnsUnmodifiableList() {
        testSubject.registerTenant(TENANT_1);

        assertThrows(UnsupportedOperationException.class,
                     () -> testSubject.tenantEventProcessors().add(tenantSegment2));
    }

    @Test
    void builderRequiresName() {
        assertThrows(Exception.class, () ->
                MultiTenantEventProcessor.builder()
                                         .tenantSegmentFactory(t -> tenantSegment1)
                                         .build()
        );
    }

    @Test
    void builderRequiresTenantSegmentFactory() {
        assertThrows(Exception.class, () ->
                MultiTenantEventProcessor.builder()
                                         .name("testProcessor")
                                         .build()
        );
    }
}
