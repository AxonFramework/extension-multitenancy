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
import org.axonframework.common.transaction.NoTransactionManager;
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a {@link SequencedDeadLetterQueue} that is aware of the tenants in the application. This
 * implementation will dead-letter queue operations to the correct tenant segment based on the provided {@link TargetTenantResolver}.
 *
 * @author Stefan Dragisic
 * @since 4.8.0
 */
public class MultiTenantDeadLetterQueue<M extends EventMessage<?>>
        implements SequencedDeadLetterQueue<M>, MultiTenantAwareComponent {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantDeadLetterQueue.class);

    private final Set<TenantDescriptor> tenants = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<TenantDescriptor, SequencedDeadLetterQueue<M>> tenantSegments = new ConcurrentHashMap<>();
    private final TargetTenantResolver<M> targetTenantResolver;
    private final String processingGroup;
    private Supplier<SequencedDeadLetterQueue<M>> deadLetterQueueSupplier = () -> null;

    /**
     * Builder class to instantiate a {@link MultiTenantDeadLetterQueue}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MultiTenantDeadLetterQueue} instance.
     */
    protected MultiTenantDeadLetterQueue(MultiTenantDeadLetterQueue.Builder<M> builder) {
        builder.validate();
        this.targetTenantResolver = builder.targetTenantResolver;
        this.processingGroup = builder.processingGroup;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MultiTenantDeadLetterQueue}.
     *
     * @return a Builder to be able to create a {@link MultiTenantDeadLetterQueue}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Registers a {@link SequencedDeadLetterQueue} that will be used by tenants.
     *
     * @param deadLetterQueue the {@link SequencedDeadLetterQueue} that will be used by tenants.
     */
    public void registerDeadLetterQueueSupplier(Supplier<SequencedDeadLetterQueue<M>> deadLetterQueue) {
        deadLetterQueueSupplier = deadLetterQueue;
    }

    /**
     * Gets the {@link SequencedDeadLetterQueue} for the given {@link TenantDescriptor}.
     * If the tenant is not registered, it will return null. If the tenant is registered, but the
     * {@link SequencedDeadLetterQueue} is not yet created, it will create it and return it.
     */
    public SequencedDeadLetterQueue<M> getTenantSegment(TenantDescriptor tenantDescriptor) {
        return tenantSegments.computeIfAbsent(tenantDescriptor, t -> {
            if (tenants.contains(tenantDescriptor)) {
                return deadLetterQueueSupplier.get();
            }
            return null;
        });
    }

    private SequencedDeadLetterQueue<M> resolveTenant(DeadLetter<? extends M> deadLetter) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(deadLetter.message(),
                                                                               tenantSegments.keySet());
        SequencedDeadLetterQueue<M> tenantDeadLetterQueue = getTenantSegment(tenantDescriptor);
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
        return resolveTenant(letterBuilder.get()).enqueueIfPresent(sequenceIdentifier, letterBuilder);
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
            return fetchFromTenantSegment(currentTenant, seg -> seg.contains(sequenceIdentifier));
        } else {
            logger.info("No tenant found for current thread. Checking if any tenant contains the sequence identifier.");
            return tenants.stream().anyMatch(tenant -> fetchFromTenantSegment(tenant,
                                                                              seg -> seg.contains(sequenceIdentifier)));
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(Object sequenceIdentifier) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, seg -> seg.deadLetterSequence(sequenceIdentifier));
        } else {
            logger.info("No tenant found for current thread. Returning all tenants dead letter sequences.");
            return tenants.stream().map(tenant -> fetchFromTenantSegment(tenant,
                                                                         seg -> seg.deadLetterSequence(
                                                                                 sequenceIdentifier)))
                          .flatMap(it -> StreamSupport.stream(it.spliterator(), false)).collect(Collectors.toList());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Iterable<DeadLetter<? extends M>>> deadLetters() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, SequencedDeadLetterQueue::deadLetters);
        } else {
            logger.info("No tenant found for current thread. Returning all tenants dead letters.");
            return tenants.stream().map(tenant -> fetchFromTenantSegment(tenant, SequencedDeadLetterQueue::deadLetters))
                          .flatMap(it -> StreamSupport.stream(it.spliterator(), false)).collect(Collectors.toList());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFull(Object sequenceIdentifier) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, seg -> seg.isFull(sequenceIdentifier));
        } else {
            logger.info("No tenant found for current thread. Checking if any of the tenants queues is full.");
            return tenants.stream().anyMatch(tenant -> fetchFromTenantSegment(tenant,
                                                                              seg -> seg.isFull(sequenceIdentifier)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, SequencedDeadLetterQueue::size);
        } else {
            logger.info("No tenant found for current thread. Returning total size of all tenants queues.");
            return tenants.stream().mapToLong(tenant -> fetchFromTenantSegment(tenant, SequencedDeadLetterQueue::size))
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
            return fetchFromTenantSegment(currentTenant, seg -> seg.sequenceSize(sequenceIdentifier));
        } else {
            logger.info("No tenant found for current thread. Returning total size of sequences.");
            return tenants.stream().filter(tenant -> fetchFromTenantSegment(tenant,
                                                                            seg -> seg.contains(sequenceIdentifier)))
                          .findFirst().map(tenant -> fetchFromTenantSegment(tenant,
                                                                            seg -> seg.sequenceSize(sequenceIdentifier)))
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
            return fetchFromTenantSegment(currentTenant, SequencedDeadLetterQueue::amountOfSequences);
        } else {
            logger.info("No tenant found for current thread. Returning total amount of all sequences from every tenant.");
            return tenants.stream().mapToLong(tenant -> fetchFromTenantSegment(tenant,
                                                                               SequencedDeadLetterQueue::amountOfSequences))
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
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, seg -> seg.process(sequenceFilter, processingTask));
        } else {
            logger.info("No tenant found for current thread. Will process a sequence for all tenants.");
            return tenants.stream().map(tenant -> fetchFromTenantSegment(tenant,
                                                                         seg -> seg.process(sequenceFilter,
                                                                                            processingTask))).reduce(
                    false,
                    (a, b) -> a || b);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, seg -> seg.process(processingTask));
        } else {
            logger.info("No tenant found for current thread. Will process a sequence for all tenants.");
            return tenants.stream().map(tenant -> fetchFromTenantSegment(tenant, seg -> seg.process(processingTask)))
                          .reduce(false, (a, b) -> a || b);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            executeForTenantSegment(currentTenant, SequencedDeadLetterQueue::clear);
        } else {
            logger.info("No tenant found for current thread. Clearing queues for all tenants.");
            tenants.forEach(tenant -> executeForTenantSegment(tenant, SequencedDeadLetterQueue::clear));
        }
    }

    private <R> R fetchFromTenantSegment(TenantDescriptor tenantDescriptor,
                                         Function<SequencedDeadLetterQueue<M>, R> function) {
        R res = new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE,
                                                  tenantDescriptor).fetchInTransaction(() -> function.apply(
                getTenantSegment(tenantDescriptor)));
        return res;
    }

    private void executeForTenantSegment(TenantDescriptor tenantDescriptor,
                                         Consumer<SequencedDeadLetterQueue<M>> consumer) {
        new TenantWrappedTransactionManager(NoTransactionManager.INSTANCE, tenantDescriptor).fetchInTransaction(() -> {
            consumer.accept(getTenantSegment(tenantDescriptor));
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        tenants.add(tenantDescriptor);
        return () -> {
            tenants.remove(tenantDescriptor);
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
     * Return the processing group that this queue is bound to.
     *
     * @return The processing group that this queue is bound to.
     */
    public String processingGroup() {
        return processingGroup;
    }

    /**
     * Builder class to instantiate a {@link MultiTenantDeadLetterQueue}.
     *
     * @param <M> the type of {@link EventMessage} contained in the {@link DeadLetter}
     */
    public static class Builder<M extends EventMessage<?>> {

        private TargetTenantResolver<M> targetTenantResolver;
        private String processingGroup;

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message}
         * message.
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
         * Sets the processing group that this queue is bound to.
         *
         * @param processingGroup The processing group that this queue is bound to.
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
