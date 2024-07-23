/*
 * Copyright (c) 2010-2024. Axon Framework
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
package org.axonframework.extensions.multitenancy.configuration;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.DirectEventProcessingStrategy;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantSubscribableMessageSource;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterProcessor;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterQueue;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterQueueFactory;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.config.EventProcessingConfigurer.PooledStreamingProcessorConfiguration.noOp;

/**
 * An extension of the {@link EventProcessingModule} that allows for the creation of
 * {@link MultiTenantEventProcessor MultiTenantEventProcessors}.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class MultiTenantEventProcessingModule extends EventProcessingModule {

    private final TenantProvider tenantProvider;
    private final MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider;

    private final MultiTenantEventProcessorPredicate multiTenantEventProcessorPredicate;

    protected final MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory;

    /**
     * Initializes a {@link MultiTenantEventProcessingModule} with a default {@link TenantProvider} and a default
     * {@link MultiTenantStreamableMessageSourceProvider}, which does not change the default
     * {@link StreamableMessageSource} for any {@link TenantDescriptor}.
     *
     * @param tenantProvider The default {@link TenantProvider} used to build {@link MultiTenantEventProcessor}s.
     */
    public MultiTenantEventProcessingModule(TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.multiTenantDeadLetterQueueFactory = null;
        this.multiTenantStreamableMessageSourceProvider = ((defaultSource, processorName, tenantDescriptor, configuration) -> defaultSource);
        this.multiTenantEventProcessorPredicate = MultiTenantEventProcessorPredicate.enableMultiTenancy();
    }

    /**
     * Initializes a {@link MultiTenantEventProcessingModule} with a default {@link TenantProvider} and a default
     * {@link MultiTenantStreamableMessageSourceProvider}, which does not change the default
     * {@link StreamableMessageSource} for any {@link TenantDescriptor}.
     *
     * @param tenantProvider                    The default {@link TenantProvider} used to build
     *                                          {@link MultiTenantEventProcessor}s.
     * @param multiTenantDeadLetterQueueFactory The {@link MultiTenantDeadLetterQueueFactory} used to build
     *                                          {@link MultiTenantDeadLetterQueue}s for each {@link TenantDescriptor}.
     */
    public MultiTenantEventProcessingModule(
            TenantProvider tenantProvider,
            MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory
    ) {
        this.tenantProvider = tenantProvider;
        this.multiTenantDeadLetterQueueFactory = multiTenantDeadLetterQueueFactory;
        multiTenantStreamableMessageSourceProvider = ((defaultSource, processorName, tenantDescriptor, configuration) -> defaultSource);
        this.multiTenantEventProcessorPredicate = MultiTenantEventProcessorPredicate.enableMultiTenancy();
    }

    /**
     * Initializes a {@link MultiTenantEventProcessingModule} with a default {@link TenantProvider} and a
     * {@link MultiTenantStreamableMessageSourceProvider}, which allows for the customization of the
     * {@link StreamableMessageSource} for each {@link TenantDescriptor}.
     *
     * @param tenantProvider                             The default {@link TenantProvider} used to build
     *                                                   {@link MultiTenantEventProcessor}s.
     * @param multiTenantStreamableMessageSourceProvider The {@link MultiTenantStreamableMessageSourceProvider} used to
     *                                                   customize the {@link StreamableMessageSource} for each
     *                                                   {@link TenantDescriptor}.
     * @param multiTenantDeadLetterQueueFactory          The {@link MultiTenantDeadLetterQueueFactory} used to build
     *                                                   {@link MultiTenantDeadLetterQueue}s for each
     *                                                   {@link TenantDescriptor}.
     */
    public MultiTenantEventProcessingModule(
            TenantProvider tenantProvider,
            MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider,
            MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory,
            MultiTenantEventProcessorPredicate multiTenantEventProcessorPredicate
    ) {
        this.tenantProvider = tenantProvider;
        this.multiTenantEventProcessorPredicate = multiTenantEventProcessorPredicate;
        this.multiTenantDeadLetterQueueFactory = multiTenantDeadLetterQueueFactory;
        this.multiTenantStreamableMessageSourceProvider = multiTenantStreamableMessageSourceProvider;
    }

    private static String getName(String name, TenantDescriptor tenantDescriptor) {
        return name + "@" + tenantDescriptor.tenantId();
    }

    /**
     * Return the {@link EventProcessor} matching the given {@code name} and {@code tenantDescriptor}. When either the
     * {@code name} or {@code tenantDescriptor} does not match any {@code EventProcessor}, an
     * {@link Optional#empty() empty Optional} is returned.
     *
     * @param name             The name of the {@link EventProcessor} to return.
     * @param tenantDescriptor The descriptor of the tenant to return an {@link EventProcessor} for.
     * @return An {@link Optional} containing the {@link EventProcessor} matching the given  {@code name} and
     * {@code tenantDescriptor}. Or an {@link Optional#empty() empty Optional} if the processor could not be found.
     */
    public Optional<EventProcessor> eventProcessor(String name, TenantDescriptor tenantDescriptor) {
        return Optional.ofNullable(this.eventProcessors().get(getName(name, tenantDescriptor)));
    }

    @Override
    public Map<String, EventProcessor> eventProcessors() {
        Map<String, EventProcessor> original = super.eventProcessors();
        Map<String, EventProcessor> allProcessors =
                original.entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().getClass().isAssignableFrom(MultiTenantEventProcessor.class))
                        .flatMap(
                                entry -> ((MultiTenantEventProcessor) entry.getValue()).tenantEventProcessors().stream()
                        )
                        .collect(Collectors.toMap(EventProcessor::getName, processor -> processor));
        allProcessors.putAll(original);
        return allProcessors;
    }

    @Override
    public EventProcessor subscribingEventProcessor(String name,
                                                    EventHandlerInvoker eventHandlerInvoker,
                                                    SubscribableMessageSource<? extends EventMessage<?>> messageSource) {

        if (!multiTenantEventProcessorPredicate.test(name)) {
           return buildSep(name, eventHandlerInvoker, messageSource);
        }

        MultiTenantEventProcessor eventProcessor =
                MultiTenantEventProcessor.builder()
                                         .name(name)
                                         .tenantSegmentFactory(
                                                 tenantDescriptor -> {
                                                     SubscribableMessageSource<? extends EventMessage<?>> tenantSource =
                                                             tenantSource(messageSource, tenantDescriptor);

                                                     return buildSep(
                                                             tenantDescriptor, name, eventHandlerInvoker, tenantSource
                                                     );
                                                 })
                                         .build();
        tenantProvider.subscribe(eventProcessor);
        return eventProcessor;
    }

    private static SubscribableMessageSource<? extends EventMessage<?>> tenantSource(
            SubscribableMessageSource<? extends EventMessage<?>> messageSource, TenantDescriptor tenantDescriptor
    ) {
        return messageSource instanceof MultiTenantSubscribableMessageSource
                ? ((MultiTenantSubscribableMessageSource<SubscribableMessageSource<EventMessage<?>>>) messageSource).tenantSegments().get(tenantDescriptor)
                : messageSource;
    }

    private SubscribingEventProcessor buildSep(String name, EventHandlerInvoker eventHandlerInvoker, SubscribableMessageSource<? extends EventMessage<?>> source, TransactionManager transactionManager) {
        return SubscribingEventProcessor.builder()
                                        .name(name)
                                        .eventHandlerInvoker(eventHandlerInvoker)
                                        .rollbackConfiguration(super.rollbackConfiguration(name))
                                        .errorHandler(super.errorHandler(name))
                                        .messageMonitor(super.messageMonitor(SubscribingEventProcessor.class, name))
                                        .messageSource(source)
                                        .processingStrategy(DirectEventProcessingStrategy.INSTANCE)
                                        .transactionManager(transactionManager)
                                        .build();
    }

    private SubscribingEventProcessor buildSep(TenantDescriptor tenantDescriptor, String name, EventHandlerInvoker eventHandlerInvoker, SubscribableMessageSource<? extends EventMessage<?>> source) {
        TransactionManager transactionManager = new TenantWrappedTransactionManager(super.transactionManager(name), tenantDescriptor);
        return buildSep(getName(name, tenantDescriptor), eventHandlerInvoker, source, transactionManager);
    }

    private SubscribingEventProcessor buildSep(String name, EventHandlerInvoker eventHandlerInvoker, SubscribableMessageSource<? extends EventMessage<?>> source) {
        return buildSep(name, eventHandlerInvoker, source, super.transactionManager(name));
    }

    @Override
    public EventProcessor trackingEventProcessor(String name,
                                                 EventHandlerInvoker eventHandlerInvoker,
                                                 TrackingEventProcessorConfiguration config,
                                                 StreamableMessageSource<TrackedEventMessage<?>> source) {
        if (!multiTenantEventProcessorPredicate.test(name)) {
            return buildTep(name, eventHandlerInvoker, source, config);
        }

        MultiTenantEventProcessor eventProcessor =
                MultiTenantEventProcessor.builder()
                                         .name(name)
                                         .tenantSegmentFactory(
                                                 tenantDescriptor -> {
                                                     StreamableMessageSource<TrackedEventMessage<?>> tenantSource =
                                                             multiTenantStreamableMessageSourceProvider.build(
                                                                     defaultSource(source, tenantDescriptor),
                                                                     name,
                                                                     tenantDescriptor,
                                                                     configuration
                                                             );

                                                     return buildTep(tenantDescriptor,
                                                                     name,
                                                                     eventHandlerInvoker,
                                                                     tenantSource,
                                                                     config);
                                                 }
                                         )
                                         .build();
        tenantProvider.subscribe(eventProcessor);
        return eventProcessor;
    }

    private TrackingEventProcessor buildTep(String name, EventHandlerInvoker eventHandlerInvoker, StreamableMessageSource<TrackedEventMessage<?>> source, TransactionManager transactionManager, TrackingEventProcessorConfiguration config) {
        return TrackingEventProcessor.builder()
                                     .name(name)
                                     .eventHandlerInvoker(eventHandlerInvoker)
                                     .rollbackConfiguration(super.rollbackConfiguration(name))
                                     .errorHandler(super.errorHandler(name))
                                     .messageMonitor(super.messageMonitor(TrackingEventProcessor.class, name))
                                     .messageSource(source)
                                     .tokenStore(super.tokenStore(name))
                                     .transactionManager(transactionManager)
                                     .trackingEventProcessorConfiguration(config)
                                     .build();
    }

    private TrackingEventProcessor buildTep(TenantDescriptor tenantDescriptor, String name, EventHandlerInvoker eventHandlerInvoker, StreamableMessageSource<TrackedEventMessage<?>> source, TrackingEventProcessorConfiguration config) {
        TransactionManager transactionManager = new TenantWrappedTransactionManager(super.transactionManager(name), tenantDescriptor);
        return buildTep(getName(name, tenantDescriptor), eventHandlerInvoker, source, transactionManager, config);
    }

    private TrackingEventProcessor buildTep(String name, EventHandlerInvoker eventHandlerInvoker, StreamableMessageSource<TrackedEventMessage<?>> source, TrackingEventProcessorConfiguration config) {
        return buildTep(name, eventHandlerInvoker, source, super.transactionManager(name), config);
    }
    @Override
    public EventProcessor pooledStreamingEventProcessor(String name,
                                                        EventHandlerInvoker eventHandlerInvoker,
                                                        Configuration config,
                                                        StreamableMessageSource<TrackedEventMessage<?>> source,
                                                        PooledStreamingProcessorConfiguration processorConfiguration) {
        if (!multiTenantEventProcessorPredicate.test(name)) {
            return psepBuilder(name, eventHandlerInvoker, source, config).build();
        }

        MultiTenantEventProcessor eventProcessor =
                MultiTenantEventProcessor.builder()
                                         .name(name)
                                         .tenantSegmentFactory(
                                                 tenantDescriptor -> {
                                                     StreamableMessageSource<TrackedEventMessage<?>> tenantSource =
                                                             defaultSource(source, tenantDescriptor);

                                                     tenantSource = multiTenantStreamableMessageSourceProvider.build(
                                                             tenantSource,
                                                             name,
                                                             tenantDescriptor,
                                                             configuration
                                                     );

                                                     PooledStreamingEventProcessor.Builder builder = psepBuilder(
                                                             tenantDescriptor,
                                                             name,
                                                             eventHandlerInvoker,
                                                             tenantSource,
                                                             config
                                                     );

                                                     return psepConfigs.getOrDefault("___DEFAULT_PSEP_CONFIG", noOp())
                                                                       .andThen(psepConfigs.getOrDefault(
                                                                               name,
                                                                               PooledStreamingProcessorConfiguration.noOp()
                                                                       ))
                                                                       .andThen(processorConfiguration)
                                                                       .apply(config, builder)
                                                                       .build();
                                                 }
                                         )
                                         .build();
        tenantProvider.subscribe(eventProcessor);
        return eventProcessor;
    }

    private static StreamableMessageSource<TrackedEventMessage<?>> defaultSource(
            StreamableMessageSource<TrackedEventMessage<?>> source, TenantDescriptor tenantDescriptor
    ) {
        return source instanceof MultiTenantEventStore
                ? ((MultiTenantEventStore) source).tenantSegments().get(tenantDescriptor)
                : source;
    }

    private PooledStreamingEventProcessor.Builder psepBuilder(String name, EventHandlerInvoker eventHandlerInvoker, StreamableMessageSource<TrackedEventMessage<?>> source, TransactionManager transactionManager, Configuration config) {
        return PooledStreamingEventProcessor.builder()
                                            .name(name)
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .rollbackConfiguration(super.rollbackConfiguration(name))
                                            .errorHandler(super.errorHandler(name))
                                            .messageMonitor(super.messageMonitor(PooledStreamingEventProcessor.class, name))
                                            .messageSource(source)
                                            .tokenStore(super.tokenStore(name))
                                            .transactionManager(transactionManager)
                                            .coordinatorExecutor(processorName -> {
                                                ScheduledExecutorService coordinatorExecutor = defaultExecutor("Coordinator[" + processorName + "]");
                                                config.onShutdown(coordinatorExecutor::shutdown);
                                                return coordinatorExecutor;
                                            })
                                            .workerExecutor(processorName -> {
                                                ScheduledExecutorService workerExecutor = defaultExecutor("WorkPackage[" + processorName + "]");
                                                config.onShutdown(workerExecutor::shutdown);
                                                return workerExecutor;
                                            });
    }

    private PooledStreamingEventProcessor.Builder psepBuilder(TenantDescriptor tenantDescriptor, String name, EventHandlerInvoker eventHandlerInvoker, StreamableMessageSource<TrackedEventMessage<?>> source, Configuration config) {
        TransactionManager transactionManager = new TenantWrappedTransactionManager(super.transactionManager(name), tenantDescriptor);
        return psepBuilder(getName(name, tenantDescriptor), eventHandlerInvoker, source, transactionManager, config);
    }

    private PooledStreamingEventProcessor.Builder psepBuilder(String name, EventHandlerInvoker eventHandlerInvoker, StreamableMessageSource<TrackedEventMessage<?>> source, Configuration config) {
        return psepBuilder(name, eventHandlerInvoker, source, super.transactionManager(name), config);
    }

    /**
     * Registers a {@link MultiTenantDeadLetterQueue} for the given {@code processingGroup}. The given
     * {@code queueBuilder} Overrides user defined queue builder and puts the {@link SequencedDeadLetterQueue} in a
     * {@link MultiTenantDeadLetterQueue}.
     *
     * @param processingGroup A {@link String} specifying the name of the processing group to register the given
     *                        {@link SequencedDeadLetterQueue} for.
     * @param queueBuilder    A builder method returning a {@link SequencedDeadLetterQueue} based on a
     *                        {@link Configuration}. The outcome is used by the given {@code processingGroup} to enqueue
     *                        and evaluate failed events in.
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    @Override
    public EventProcessingConfigurer registerDeadLetterQueue(
            @Nonnull String processingGroup,
            @Nonnull Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>> queueBuilder
    ) {
        if (multiTenantDeadLetterQueueFactory == null) {
            throw new AxonConfigurationException(
                    "Cannot register a DeadLetterQueue without a MultiTenantDeadLetterQueueFactory"
            );
        }
        MultiTenantDeadLetterQueue<EventMessage<?>> deadLetterQueue = multiTenantDeadLetterQueueFactory
                .getDeadLetterQueue(processingGroup);
        deadLetterQueue.registerDeadLetterQueueSupplier(() -> queueBuilder.apply(configuration));
        return super.registerDeadLetterQueue(processingGroup, configuration -> deadLetterQueue);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the {@link SequencedDeadLetterProcessor} in a {@link MultiTenantDeadLetterProcessor}, which will delegate
     * the processing of the dead letter to the {@link SequencedDeadLetterProcessor} for the tenant of the failed event.
     * Enabling the {@link SequencedDeadLetterProcessor} to process dead letters for multiple tenants.
     * <p>
     * It is necessary to invoke {@code forTenant} method on the returned {@link SequencedDeadLetterProcessor} to
     * specify the tenant for which the dead letter should be processed.
     *
     * @param processingGroup The name of the processing group to register the {@link SequencedDeadLetterProcessor}
     *                        for.
     */
    @Override
    public Optional<SequencedDeadLetterProcessor<EventMessage<?>>> sequencedDeadLetterProcessor(
            @Nonnull String processingGroup
    ) {
        return super.sequencedDeadLetterProcessor(processingGroup)
                    .map(MultiTenantDeadLetterProcessor::new);
    }

    private ScheduledExecutorService defaultExecutor(String factoryName) {
        return Executors.newScheduledThreadPool(1, new AxonThreadFactory(factoryName));
    }


}
