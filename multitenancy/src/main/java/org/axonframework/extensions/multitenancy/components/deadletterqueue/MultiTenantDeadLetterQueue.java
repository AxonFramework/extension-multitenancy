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
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a {@link SequencedDeadLetterQueue} that is aware of the tenants in the application. This
 * implementation will delegate the dead letter queue to the correct tenant segment based on the
 * {@link TargetTenantResolver} provided.
 *
 * @author Stefan Dragisic
 * @since 4.8.0
 */
public class MultiTenantDeadLetterQueue<M extends EventMessage<?>> implements SequencedDeadLetterQueue<M>,
        MultiTenantAwareComponent {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantDeadLetterQueue.class);

    private final Map<TenantDescriptor, Supplier<SequencedDeadLetterQueue<M>>> tenantSegments = new ConcurrentHashMap<>();

    private final AtomicReference<Supplier<SequencedDeadLetterQueue<M>>> registeredDeadLetterQueue = new AtomicReference<>();
    private final TargetTenantResolver<M> targetTenantResolver;
    private final String processingGroup;

    /**
     * Builder class to instantiate a {@link MultiTenantDeadLetterQueue}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MultiTenantDeadLetterQueue} instance.
     */
    public MultiTenantDeadLetterQueue(MultiTenantDeadLetterQueue.Builder<M> builder) {
        builder.validate();
        this.targetTenantResolver = builder.targetTenantResolver;
        this.processingGroup = builder.processingGroup;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MultiTenantDeadLetterQueue}.
     *
     * @return a Builder to be able to create a {@link MultiTenantDeadLetterQueue}.
     */
    public static MultiTenantDeadLetterQueue.Builder builder() {
        return new MultiTenantDeadLetterQueue.Builder();
    }

    /**
     * Registers a {@link SequencedDeadLetterQueue} that will be used by tenants.
     *
     * @param deadLetterQueue the {@link SequencedDeadLetterQueue} that will be used by tenants.
     */
    public void registerDeadLetterQueue(Supplier<SequencedDeadLetterQueue<M>> deadLetterQueue) {
        registeredDeadLetterQueue.set(deadLetterQueue);
    }

    /**
     * Returns all available tenant segments of the {@link SequencedDeadLetterQueue}.
     *
     * @return all available tenant segments of the {@link SequencedDeadLetterQueue}.
     */
    public Map<TenantDescriptor, Supplier<SequencedDeadLetterQueue<M>>> getTenantSegments() {
        return Collections.unmodifiableMap(tenantSegments);
    }

    private SequencedDeadLetterQueue<M> resolveTenant(DeadLetter<? extends M> deadLetter) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(deadLetter.message(),
                                                                               tenantSegments.keySet());
        SequencedDeadLetterQueue<M> tenantDeadLetterQueue = tenantSegments.get(tenantDescriptor).get();
        if (tenantDeadLetterQueue == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantDeadLetterQueue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enqueue(Object sequenceIdentifier, DeadLetter<? extends M> letter)
            throws DeadLetterQueueOverflowException {
        resolveTenant(letter).enqueue(sequenceIdentifier, letter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enqueueIfPresent(Object sequenceIdentifier, Supplier<DeadLetter<? extends M>> letterBuilder)
            throws DeadLetterQueueOverflowException {
        return resolveTenant(letterBuilder.get())
                .enqueueIfPresent(sequenceIdentifier, letterBuilder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void evict(DeadLetter<? extends M> letter) {
        resolveTenant(letter).evict(letter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requeue(DeadLetter<? extends M> letter, UnaryOperator<DeadLetter<? extends M>> letterUpdater)
            throws NoSuchDeadLetterException {
        resolveTenant(letter).requeue(letter, letterUpdater);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(Object sequenceIdentifier) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return tenantSegments.get(currentTenant).get().contains(sequenceIdentifier);
        } else {
            logger.info("No tenant found for current thread. Checking if any tenant contains the sequence identifier.");
            return tenantSegments.values()
                                 .stream()
                                 .anyMatch(it -> it.get().contains(sequenceIdentifier));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(Object sequenceIdentifier) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return tenantSegments.get(currentTenant).get().deadLetterSequence(sequenceIdentifier);
        } else {
            logger.info("No tenant found for current thread. Returning all tenants dead letter sequences.");
            return tenantSegments.values()
                                 .stream()
                                 .map(Supplier::get)
                                 .map(it -> it.deadLetterSequence(sequenceIdentifier))
                                 .flatMap(it -> StreamSupport.stream(it.spliterator(), false))
                                 .collect(Collectors.toList());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Iterable<DeadLetter<? extends M>>> deadLetters() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return tenantSegments.get(currentTenant).get().deadLetters();
        } else {
            logger.info("No tenant found for current thread. Returning all tenants dead letters.");
            return tenantSegments.values()
                                 .stream()
                                 .map(Supplier::get)
                                 .map(SequencedDeadLetterQueue::deadLetters)
                                 .flatMap(it -> StreamSupport.stream(it.spliterator(), false))
                                 .collect(Collectors.toList());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFull(Object sequenceIdentifier) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return tenantSegments.get(currentTenant).get().isFull(sequenceIdentifier);
        } else {
            logger.info("No tenant found for current thread. Checking if any of the tenants queues is full.");
            return tenantSegments.values()
                                 .stream()
                                 .anyMatch(tenant -> tenant.get().isFull(sequenceIdentifier));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return tenantSegments.get(currentTenant).get().size();
        } else {
            logger.info("No tenant found for current thread. Returning total size of all tenants queues.");
            return tenantSegments.values()
                                 .stream()
                                 .map(Supplier::get)
                                 .mapToLong(SequencedDeadLetterQueue::size)
                                 .sum();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long sequenceSize(Object sequenceIdentifier) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return tenantSegments.get(currentTenant).get().sequenceSize(sequenceIdentifier);
        } else {
            logger.info("No tenant found for current thread. Returning total size of sequences.");
            return tenantSegments.values()
                                 .stream()
                                 .filter(it -> it.get().contains(sequenceIdentifier))
                                 .findFirst().map(it -> it.get().sequenceSize(sequenceIdentifier))
                                 .orElse(0L);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long amountOfSequences() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return tenantSegments.get(currentTenant).get().amountOfSequences();
        } else {
            logger.info("No tenant found for current thread. Returning total amount of sequences.");
            return tenantSegments.values()
                                 .stream()
                                 .map(Supplier::get)
                                 .mapToLong(SequencedDeadLetterQueue::amountOfSequences)
                                 .sum();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(Predicate<DeadLetter<? extends M>> sequenceFilter,
                           Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant == null) {
            logger.warn("No tenant found for current thread. No dead letters will be processed.");
            return false;
        }
        return tenantSegments.get(currentTenant).get().process(sequenceFilter, processingTask);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant == null) {
            logger.warn("No tenant found for current thread. No dead letters will be processed.");
            return false;
        }
        return tenantSegments.get(currentTenant).get().process(processingTask);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant == null) {
            logger.warn("No tenant found for current thread. No dead letters will be cleared.");
            return;
        }
        tenantSegments.get(currentTenant).get().clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.putIfAbsent(tenantDescriptor, registeredDeadLetterQueue.get());
        return () -> {
            tenantSegments.remove(tenantDescriptor);
            return true;
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }

    /**
     * Return processing group that this queue is bounded to.
     *
     * @return processing group that this queue is bounded to.
     */
    public String processingGroup() {
        return processingGroup;
    }

    /**
     * Builder class to instantiate a {@link MultiTenantDeadLetterQueue}.
     * @param <M> the type of {@link EventMessage} contained in the {@link DeadLetter}
     */
    public static class Builder<M extends EventMessage<?>> {

        public TargetTenantResolver<M> targetTenantResolver;
        public String processingGroup;

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message}
         * message
         *
         * @param targetTenantResolver used to resolve correct tenant segment based on {@link Message} message
         * @return the current Builder instance, for fluent interfacing
         */
        public MultiTenantDeadLetterQueue.Builder<M> targetTenantResolver(
                TargetTenantResolver<M> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        /**
         * Sets the processing group that this queue is bounded to.
         * @param processingGroup the processing group that this queue is bounded to.
         * @return the current Builder instance, for fluent interfacing
         */
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
        }
    }
}
