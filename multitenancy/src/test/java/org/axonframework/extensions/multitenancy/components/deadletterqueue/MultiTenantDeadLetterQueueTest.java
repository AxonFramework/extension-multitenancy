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

package org.axonframework.extensions.multitenancy.components.deadletterqueue;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class MultiTenantDeadLetterQueueTest {

    private MultiTenantDeadLetterQueue<EventMessage<?>> subject;
    private ArrayList<SequencedDeadLetterQueue<EventMessage<?>>> deadLetterQueues;
    @BeforeEach
    void setUp() {
        TargetTenantResolver<EventMessage<?>> targetTenantResolver = (m, tenants) -> TenantDescriptor.tenantWithId(
                "tenant-send-to");

        deadLetterQueues = new ArrayList<>();

        subject = MultiTenantDeadLetterQueue
                .builder()
                .processingGroup("test")
                .targetTenantResolver(targetTenantResolver)
                .build();

        subject.registerDeadLetterQueueSupplier(() -> {
            SequencedDeadLetterQueue mock = mock(SequencedDeadLetterQueue.class);
            deadLetterQueues.add(mock);
            return mock;
        });

        subject.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant-send-to"));
    }



    @Test
    void testInit() {
        assertEquals(subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to")), deadLetterQueues.stream().findFirst().get());
    }

    @Test
    void testTwoTenants() {
        subject.registerAndStartTenant(TenantDescriptor.tenantWithId("second-tenant"));
        SequencedDeadLetterQueue<EventMessage<?>> firstTenantSegment = subject.getTenantSegment(TenantDescriptor.tenantWithId(
                "tenant-send-to"));
        SequencedDeadLetterQueue<EventMessage<?>> secondTenantSegment = subject.getTenantSegment(TenantDescriptor.tenantWithId(
                "second-tenant"));
        assertTrue(deadLetterQueues.contains(firstTenantSegment));
        assertTrue(deadLetterQueues.contains(secondTenantSegment));
        assertEquals(2,deadLetterQueues.size());
    }

    @Test
    void testEnqueue() {
        DeadLetter deadLetter = mock(DeadLetter.class);
        subject.enqueue("id", deadLetter);
        SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue = deadLetterQueues.stream().findFirst().get();
        verify(deadLetterQueue).enqueue("id",deadLetter);
    }

    @Test
    void enqueueIfPresent() {
        DeadLetter<? extends EventMessage<?>> deadLetter = mock(DeadLetter.class);
        Supplier<DeadLetter<? extends EventMessage<?>>> deadLetterSupplier = () -> deadLetter;
        subject.enqueueIfPresent("id", deadLetterSupplier);
        SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue = deadLetterQueues.stream().findFirst().get();
        verify(deadLetterQueue).enqueueIfPresent("id", deadLetterSupplier);
    }

    @Test
    void registerDeadLetterQueueSupplier() {
        AtomicInteger counter = new AtomicInteger(0);
        subject.registerDeadLetterQueueSupplier(() -> {
            counter.incrementAndGet();
            SequencedDeadLetterQueue mock = mock(SequencedDeadLetterQueue.class);
            deadLetterQueues.add(mock);
            return mock;
        });

        subject.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant1"));
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant1"));
        assertEquals(1, counter.get());

        subject.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant2"));
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant2"));
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant2"));
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant2"));
        assertEquals(2, counter.get());
    }


    @Test
    void evict() {
        DeadLetter deadLetter = mock(DeadLetter.class);
        subject.evict(deadLetter);
        SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue = deadLetterQueues.stream().findFirst().get();
        verify(deadLetterQueue).evict(deadLetter);
    }

    @Test
    void requeue() {
        DeadLetter deadLetter = mock(DeadLetter.class);
        subject.requeue(deadLetter, d -> d );
        SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue = deadLetterQueues.stream().findFirst().get();
        verify(deadLetterQueue).requeue(eq(deadLetter), any());
    }

    @Test
    void containsSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                                         secondTenant).fetchInTransaction(() -> subject.contains("id"));


        verify(deadLetterQueues.get(0), times(0)).contains("id");
        verify(deadLetterQueues.get(1), times(1)).contains("id");
    }

    @Test
    void containsAllTenants() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        subject.contains("id");
        deadLetterQueues.forEach(q-> verify(q, times(1)).contains("id"));
    }

    @Test
    void deadLetterSequenceSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> subject.deadLetterSequence("id"));


        verify(deadLetterQueues.get(0), times(0)).deadLetterSequence("id");
        verify(deadLetterQueues.get(1), times(1)).deadLetterSequence("id");
    }

    @Test
    void deadLetterSequenceAllTenants() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        subject.deadLetterSequence("id");

        deadLetterQueues.forEach(q-> verify(q, times(1)).deadLetterSequence("id"));
    }

    @Test
    void deadLettersSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> subject.deadLetters());


        verify(deadLetterQueues.get(0), times(0)).deadLetters();
        verify(deadLetterQueues.get(1), times(1)).deadLetters();
    }

    @Test
    void deadLettersAllTenants() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        subject.deadLetters();

        deadLetterQueues.forEach(q-> verify(q, times(1)).deadLetters());
    }

    @Test
    void isFullSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> subject.isFull("id"));


        verify(deadLetterQueues.get(0), times(0)).isFull("id");
        verify(deadLetterQueues.get(1), times(1)).isFull("id");
    }

    @Test
    void isFullAllTenants() {
        deadLetterQueues.forEach(q-> when(q.isFull(any())).thenReturn(false));

        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        subject.isFull("id");

        deadLetterQueues.forEach(q-> verify(q, times(1)).isFull("id"));
    }

    @Test
    void sizeSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> subject.size());


        verify(deadLetterQueues.get(0), times(0)).size();
        verify(deadLetterQueues.get(1), times(1)).size();
    }

    @Test
    void sizeAllTenants() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        deadLetterQueues.forEach(q-> when(q.size()).thenReturn(10L));
        assertEquals(20, subject.size());

        deadLetterQueues.forEach(q-> verify(q, times(1)).size());
    }

    @Test
    void sequenceSizeSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> subject.sequenceSize("id"));


        verify(deadLetterQueues.get(0), times(0)).sequenceSize("id");
        verify(deadLetterQueues.get(1), times(1)).sequenceSize("id");
    }

    @Test
    void sequenceSizeAllTenants() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        deadLetterQueues.forEach(q-> when(q.contains("id")).thenReturn(true));
        deadLetterQueues.forEach(q-> when(q.sequenceSize("id")).thenReturn(10L));
        assertEquals(10, subject.sequenceSize("id")); //finds first tenant with sequence

        deadLetterQueues.stream().findFirst().ifPresent(q-> verify(q, times(1)).sequenceSize(  "id"));
    }

    @Test
    void amountOfSequencesSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> subject.amountOfSequences());


        verify(deadLetterQueues.get(0), times(0)).amountOfSequences();
        verify(deadLetterQueues.get(1), times(1)).amountOfSequences();
    }

    @Test
    void amountOfSequencesAllTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        deadLetterQueues.forEach(q-> when(q.amountOfSequences()).thenReturn(10L));
        assertEquals(20, subject.amountOfSequences());

        deadLetterQueues.forEach(q-> verify(q, times(1)).amountOfSequences());
    }

    @Test
    void processSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> subject.process(m -> true, (d) -> Decisions.evict()));

        verify(deadLetterQueues.get(0), times(0)).process(any(),any());
        verify(deadLetterQueues.get(1), times(1)).process(any(),any());
    }

    @Test
    void processSingleAllTenants() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        deadLetterQueues.forEach(q-> when(q.process(any(),any())).thenReturn(true));
        assertEquals(true, subject.process(m -> true, (d) -> Decisions.evict()));

        deadLetterQueues.forEach(q-> verify(q, times(1)).process(any(),any()));
    }

    @Test
    void clearSingleTenant() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                            secondTenant).fetchInTransaction(() -> {
                                                subject.clear();
                                                return null;
        });

        verify(deadLetterQueues.get(0), times(0)).clear();
        verify(deadLetterQueues.get(1), times(1)).clear();
    }

    @Test
    void clearAllTenants() {
        TenantDescriptor secondTenant = TenantDescriptor.tenantWithId("tenant-second-tenant");
        subject.registerTenant(secondTenant);
        subject.getTenantSegment(TenantDescriptor.tenantWithId("tenant-send-to"));
        subject.getTenantSegment(secondTenant);

        deadLetterQueues.forEach(q-> doNothing().when(q).clear());
        subject.clear();

        deadLetterQueues.forEach(q-> verify(q, times(1)).clear());
    }
}