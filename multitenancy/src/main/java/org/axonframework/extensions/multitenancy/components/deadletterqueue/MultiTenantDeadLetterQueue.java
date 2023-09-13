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
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a {@link SequencedDeadLetterQueue} that is aware of the tenants in the application. This
 * implementation will delegate dead-letter queue operations to the correct tenant segment based on the provided
 * {@link TargetTenantResolver}.
 *
 * @param <M> An implementation of {@link EventMessage} contained in the {@link DeadLetter dead letters} within this
 *            queue.
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
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantDeadLetterQueue} instance.
     */
    protected MultiTenantDeadLetterQueue(MultiTenantDeadLetterQueue.Builder<M> builder) {
        builder.validate();
        this.targetTenantResolver = builder.targetTenantResolver;
        this.processingGroup = builder.processingGroup;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MultiTenantDeadLetterQueue}.
     * <p>
     * The {@link Builder#processingGroup(String) processingGroup} and
     * {@link Builder#targetTenantResolver(TargetTenantResolver) TargetTenantResolver} are <b>hard requirements</b> and
     * as such should be provided.
     *
     * @return a Builder to be able to create a {@link MultiTenantDeadLetterQueue}.
     */
    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    /**
     * Registers a builder function for {@link SequencedDeadLetterQueue SequencedDeadLetterQueues} that will be used by
     * the tenants of this instance.
     *
     * @param deadLetterQueue A builder function for {@link SequencedDeadLetterQueue SequencedDeadLetterQueues} that
     *                        will be used by the tenants of this instance.
     */
    public void registerDeadLetterQueueSupplier(Supplier<SequencedDeadLetterQueue<M>> deadLetterQueue) {
        deadLetterQueueSupplier = deadLetterQueue;
    }

    /**
     * Gets the {@link SequencedDeadLetterQueue} for the given {@link TenantDescriptor}. If the tenant is not
     * registered, it will return null. If the tenant is registered, but the {@link SequencedDeadLetterQueue} is not yet
     * created, it will create it and return it.
     *
     * @param tenantDescriptor the {@link TenantDescriptor} for which to get the {@link SequencedDeadLetterQueue}.
     * @return the {@link SequencedDeadLetterQueue} for the given {@link TenantDescriptor}.
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
    public void enqueue(@Nonnull Object sequenceIdentifier,
                        @Nonnull DeadLetter<? extends M> letter) throws DeadLetterQueueOverflowException {
        resolveTenant(letter).enqueue(sequenceIdentifier, letter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enqueueIfPresent(
            @Nonnull Object sequenceIdentifier,
            Supplier<DeadLetter<? extends M>> letterBuilder
    ) throws DeadLetterQueueOverflowException {
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
    public void requeue(
            @Nonnull DeadLetter<? extends M> letter,
            @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater
    ) throws NoSuchDeadLetterException {
        resolveTenant(letter).requeue(letter, letterUpdater);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If executed in tenant aware transaction, it will check if the current tenant contains the sequence identifier,
     * otherwise it will check if any registered tenant contains the sequence identifier.
     */
    @Override
    public boolean contains(@Nonnull Object sequenceIdentifier) {
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
     * <p>
     * If executed in tenant aware transaction, it will return all the {@link DeadLetter dead letters} for the given
     * {@code sequenceIdentifier} for current tenant, otherwise it will return all the {@link DeadLetter dead letters}
     * for the given {@code sequenceIdentifier} for any registered tenant that contains the sequence identifier.
     *
     * @param sequenceIdentifier The identifier of the sequence of {@link DeadLetter dead letters}to return.
     * @return All the {@link DeadLetter dead letters} for the given {@code sequenceIdentifier} in insert order.
     */
    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, seg -> seg.deadLetterSequence(sequenceIdentifier));
        } else {
            logger.info("No tenant found for current thread. Returning all tenants dead letter sequences.");
            return tenants.stream()
                          .filter(tenant -> fetchFromTenantSegment(tenant, seg -> seg.contains(sequenceIdentifier)))
                          .map(tenant -> fetchFromTenantSegment(tenant,
                                                                seg -> seg.deadLetterSequence(sequenceIdentifier)))
                          .flatMap(it -> StreamSupport.stream(it.spliterator(), false)).collect(Collectors.toList());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If executed in tenant aware transaction, it will return all {@link DeadLetter dead letter} sequences held by
     * current tenant, otherwise it will return all {@link DeadLetter dead letter} sequences held by all registered
     * tenant.
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
     * <p>
     * If executed in tenant aware transaction, it will check if the current tenant queue is full, otherwise it will
     * check if any registered tenant queue is full.
     */
    @Override
    public boolean isFull(@Nonnull Object sequenceIdentifier) {
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
     * <p>
     * If executed in tenant aware transaction, it will return the number of dead letters contained in current tenant
     * queue, otherwise it will return the number of dead letters contained in all registered tenants queues.
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
     * <p>
     * If executed in tenant aware transaction, it will return the number of dead letters for the sequence matching the
     * given {@code sequenceIdentifier} contained in current tenant queue, otherwise it will return the number of dead
     * letters for the sequence matching the given {@code sequenceIdentifier} contained in any registered tenant queue,
     * that contains the sequence identifier.
     */
    @Override
    public long sequenceSize(@Nonnull Object sequenceIdentifier) {
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
     * <p>
     * If executed in tenant aware transaction, it will return the number of unique sequences contained in current
     * tenant queue, otherwise it will return the number of unique sequences contained in all registered tenants.
     */
    @Override
    public long amountOfSequences() {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, SequencedDeadLetterQueue::amountOfSequences);
        } else {
            logger.info("No tenant found for current thread. Returning total amount of all sequences from every tenant.");
            return tenants.stream()
                          .mapToLong(tenant -> fetchFromTenantSegment(
                                  tenant, SequencedDeadLetterQueue::amountOfSequences
                          ))
                          .sum();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If executed in tenant aware transaction, it will process a sequence of enqueued {@link DeadLetter dead letters}
     * through the given {@code processingTask} matching the {@code sequenceFilter} from current tenant queue, otherwise
     * it will process a sequence of enqueued {@link DeadLetter dead letters} through the given {@code processingTask}
     * matching the {@code sequenceFilter} from all registered tenants queues.
     */
    @Override
    public boolean process(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                           @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, seg -> seg.process(sequenceFilter, processingTask));
        } else {
            logger.info("No tenant found for current thread. Will process a sequence for all tenants.");
            return tenants.stream()
                          .map(tenant -> fetchFromTenantSegment(
                                  tenant, seg -> seg.process(sequenceFilter, processingTask)
                          ))
                          .reduce(false, (a, b) -> a || b);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If executed in tenant aware transaction, it will process a sequence of enqueued {@link DeadLetter dead letters}
     * with the given {@code processingTask} from current tenant queue, otherwise it will process a sequence of enqueued
     * {@link DeadLetter dead letters} with the given {@code processingTask} from all registered tenants queues.
     */
    @Override
    public boolean process(@Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        TenantDescriptor currentTenant = TenantWrappedTransactionManager.getCurrentTenant();
        if (currentTenant != null) {
            return fetchFromTenantSegment(currentTenant, seg -> seg.process(processingTask));
        } else {
            logger.info("No tenant found for current thread. Will process a sequence for all tenants.");
            return tenants.stream()
                          .map(tenant -> fetchFromTenantSegment(tenant, seg -> seg.process(processingTask)))
                          .reduce(false, (a, b) -> a || b);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If executed in tenant aware transaction, it will clear out all {@link DeadLetter dead letters} present in current
     * tenant queue, otherwise it will clear out all {@link DeadLetter dead letters} all registered tenants queues.
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
                                         Function<SequencedDeadLetterQueue<M>, R> fetchBlock) {
        return new TenantWrappedTransactionManager(tenantDescriptor)
                .fetchInTransaction(() -> fetchBlock.apply(getTenantSegment(tenantDescriptor)));
    }

    private void executeForTenantSegment(TenantDescriptor tenantDescriptor,
                                         Consumer<SequencedDeadLetterQueue<M>> executeBlock) {
        new TenantWrappedTransactionManager(tenantDescriptor).fetchInTransaction(() -> {
            executeBlock.accept(getTenantSegment(tenantDescriptor));
            return null;
        });
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        tenants.add(tenantDescriptor);
        return () -> {
            tenants.remove(tenantDescriptor);
            tenantSegments.remove(tenantDescriptor);
            return true;
        };
    }

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
     * <p>
     * The {@link #processingGroup(String) processingGroup} and
     * {@link #targetTenantResolver(TargetTenantResolver) TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @param <M> The type of {@link EventMessage} contained in the {@link DeadLetter}.
     */
    public static class Builder<M extends EventMessage<?>> {

        private String processingGroup;
        private TargetTenantResolver<M> targetTenantResolver;

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message}
         * message.
         * <p>
         * This is a <b>hard requirement</b> and as such should be provided.
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
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantDeadLetterQueue} as specified through this Builder.
         *
         * @return a {@link MultiTenantDeadLetterQueue} as specified through this Builder
         */
        public MultiTenantDeadLetterQueue<M> build() {
            return new MultiTenantDeadLetterQueue<>(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         */
        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
        }
    }
}
