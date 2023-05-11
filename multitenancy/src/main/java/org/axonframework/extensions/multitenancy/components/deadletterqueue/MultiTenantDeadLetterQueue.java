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

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueueOverflowException;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.NoSuchDeadLetterException;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a {@link SequencedDeadLetterQueue} that is aware of the tenants in the application. This
 * implementation will delegate the dead letter queue to the correct tenant segment based on the {@link
 * TargetTenantResolver} provided.
 *
 * @author Stefan Dragisic
 * @since 4.8.0
 */
public class MultiTenantDeadLetterQueue <M extends EventMessage<?>> implements SequencedDeadLetterQueue<M>,
        MultiTenantAwareComponent {

    private final Map<TenantDescriptor, Supplier<SequencedDeadLetterQueue<M>>> tenantSegments = new ConcurrentHashMap<>();

    private final AtomicReference<Supplier<SequencedDeadLetterQueue<M>>> registeredDeadLetterQueue = new AtomicReference<>();
    private final TargetTenantResolver<M> targetTenantResolver;

    private final String processingGroup;

    public MultiTenantDeadLetterQueue(MultiTenantDeadLetterQueue.Builder<M> builder) {
        builder.validate();
        this.targetTenantResolver = builder.targetTenantResolver;
        this.processingGroup = builder.processingGroup;
    }

    public static MultiTenantDeadLetterQueue.Builder builder() {
        return new MultiTenantDeadLetterQueue.Builder();
    }

    /**
     * Registers a {@link SequencedDeadLetterQueue} that will be used by tenants.
     * @param deadLetterQueue
     */
    public void registerDeadLetterQueue(Supplier<SequencedDeadLetterQueue<M>> deadLetterQueue) {
        //atomic reference to supplier
        registeredDeadLetterQueue.set(deadLetterQueue);
    }


    public Map<TenantDescriptor, Supplier<SequencedDeadLetterQueue<M>>> getTenantSegments() {
        return Collections.unmodifiableMap(tenantSegments);
    }

    private SequencedDeadLetterQueue<M> resolveTenant(DeadLetter<? extends M> deadLetter) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(deadLetter.message(), tenantSegments.keySet());
        SequencedDeadLetterQueue<M> tenantDeadLetterQueue = tenantSegments.get(tenantDescriptor).get();
        if (tenantDeadLetterQueue == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantDeadLetterQueue;
    }

    @Override
    public void enqueue(Object sequenceIdentifier, DeadLetter<? extends M> letter)
            throws DeadLetterQueueOverflowException {
        resolveTenant(letter).enqueue(sequenceIdentifier, letter);
    }

    @Override
    public boolean enqueueIfPresent(Object sequenceIdentifier, Supplier<DeadLetter<? extends M>> letterBuilder)
            throws DeadLetterQueueOverflowException {
        return resolveTenant(letterBuilder.get())
                .enqueueIfPresent(sequenceIdentifier, letterBuilder);
    }

    @Override
    public void evict(DeadLetter<? extends M> letter) {
        resolveTenant(letter).evict(letter);
    }

    @Override
    public void requeue(DeadLetter<? extends M> letter, UnaryOperator<DeadLetter<? extends M>> letterUpdater)
            throws NoSuchDeadLetterException {
        resolveTenant(letter).requeue(letter, letterUpdater);
    }

    @Override
    public boolean contains(Object sequenceIdentifier) {
        throw new UnsupportedOperationException("Not supported. Use method on tenant specific queue.");
    }

    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(Object sequenceIdentifier) {
        throw new UnsupportedOperationException("Not supported. Use method on tenant specific queue.");
    }

    @Override
    public Iterable<Iterable<DeadLetter<? extends M>>> deadLetters() {
        throw new UnsupportedOperationException("Not supported. Use method on tenant specific queue.");
    }

    @Override
    public boolean isFull(Object sequenceIdentifier) {
        throw new UnsupportedOperationException("Not supported. Use method on tenant specific queue.");
    }

    @Override
    public long size() {
        throw new UnsupportedOperationException("Not supported. Use method on tenant specific queue.");
    }

    @Override
    public long sequenceSize(Object sequenceIdentifier) {
        throw new UnsupportedOperationException("Not supported. Use method on tenant specific queue.");
    }

    @Override
    public long amountOfSequences() {
        throw new UnsupportedOperationException("Not supported. Use method on tenant specific queue.");
    }


    @Override
    public boolean process(Predicate<DeadLetter<? extends M>> sequenceFilter,
                           Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        return tenantSegments.entrySet().stream().allMatch(entry->{
            TenantDescriptor tenantDescriptor = entry.getKey();

            SequencedDeadLetterQueue<M> deadLetterQueueSupplier = entry.getValue().get();

            return deadLetterQueueSupplier.process(sequenceFilter, processingTask);
        });
    }

    @Override
    public boolean process(Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        return tenantSegments.entrySet().stream().allMatch(entry->{
            TenantDescriptor tenantDescriptor = entry.getKey(); //to be used
            SequencedDeadLetterQueue<M> deadLetterQueueSupplier = entry.getValue().get();

            return deadLetterQueueSupplier.process(processingTask);
        });
    }

    @Override
    public void clear() {
        tenantSegments.forEach((tenantDescriptor, value) -> {
            SequencedDeadLetterQueue<M> deadLetterQueueSupplier = value.get();
            deadLetterQueueSupplier.clear();
        });
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        //to be used to create queue tenantDescriptor
        tenantSegments.putIfAbsent(tenantDescriptor, registeredDeadLetterQueue.get());
        return () -> {
            tenantSegments.remove(tenantDescriptor);
            return true;
        };
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }

    public static class Builder <M extends EventMessage<?>> {

        public TargetTenantResolver<M> targetTenantResolver;
        public String processingGroup;

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message}
         * message
         *
         * @param targetTenantResolver used to resolve correct tenant segment based on {@link Message} message
         * @return the current Builder instance, for fluent interfacing
         */
        public MultiTenantDeadLetterQueue.Builder<M> targetTenantResolver(TargetTenantResolver<M> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        public MultiTenantDeadLetterQueue.Builder<M> processingGroup(String processingGroup) {
            BuilderUtils.assertNonNull(processingGroup, "The processingGroup is a hard requirement");
            this.processingGroup = processingGroup;
            return this;
        }

        public MultiTenantDeadLetterQueue<M> build() {
            return new MultiTenantDeadLetterQueue<>(this);
        }

        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            assertNonNull(processingGroup, "The ProcessingGroup is a hard requirement");
        }
    }

}
