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
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;

import java.util.function.Predicate;

/**
 * Utility class, implementation of a {@link SequencedDeadLetterProcessor} that is used to invoke process operation on a correct tenant segment.
 * This implementation will delegate the method to the correct tenant segment based on the {@link
 * TenantDescriptor} provided.
 *
 * @author Stefan Dragisic
 * @since 4.8.0
 */
public class MultiTenantDeadLetterProcessor
        implements SequencedDeadLetterProcessor<EventMessage<?>> {

    private final TenantDescriptor tenantDescriptor;

    private final SequencedDeadLetterProcessor<EventMessage<?>> delegate;

    /**
     * Creates a {@link MultiTenantDeadLetterProcessor}
     * for the given {@link TenantDescriptor} and {@link SequencedDeadLetterProcessor} delegate.
     *
     * @param tenantDescriptor The {@link TenantDescriptor} used to determine the correct tenant segment
     * @param delegate The {@link SequencedDeadLetterProcessor} delegate
     */
    public MultiTenantDeadLetterProcessor(TenantDescriptor tenantDescriptor,
                                           SequencedDeadLetterProcessor<EventMessage<?>> delegate) {
        this.tenantDescriptor = tenantDescriptor;
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(Predicate<DeadLetter<? extends EventMessage<?>>> sequenceFilter) {
        return new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE, tenantDescriptor)
                .fetchInTransaction(() -> delegate.process(sequenceFilter));
    }
}
