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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.junit.jupiter.api.*;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class MultiTenantDeadLetterProcessorTest {

    private MultiTenantDeadLetterProcessor subject;

    private SequencedDeadLetterProcessor<EventMessage<?>> delegate;

    @BeforeEach
    void setUp() {
        delegate = mock(SequencedDeadLetterProcessor.class);
        subject = new MultiTenantDeadLetterProcessor(delegate);
    }

    @Test
    void forTenantMustBeCalled() {
        assertThrows(IllegalStateException.class, () -> {
            subject.process(t -> true);
        });

        subject.forTenant(TenantDescriptor.tenantWithId("tenantId"))
               .processAny();

        verify(delegate).processAny();
    }

    @Test
    void processHasTenantAttached() {
        Predicate<DeadLetter<? extends EventMessage<?>>> predicate = t -> {
            assertEquals(TenantDescriptor.tenantWithId("tenantId"),
                         TenantWrappedTransactionManager.getCurrentTenant());
            return true;
        };
        subject.forTenant(TenantDescriptor.tenantWithId("tenantId"))
               .process( predicate);

        verify(delegate).process(predicate);
    }
}