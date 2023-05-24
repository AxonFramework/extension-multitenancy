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
package org.axonframework.extensions.multitenancy.configuration;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
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
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An extension of the {@link EventProcessingModule} that allows for the creation of {@link MultiTenantEventProcessor}s.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class MultiTenantEventProcessingModule extends EventProcessingModule {

    private final TenantProvider tenantProvider;
    private final MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider;

    protected final MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory;

    /**
     * Initializes a {@link MultiTenantEventProcessingModule} with a default {@link TenantProvider} and a default {@link MultiTenantStreamableMessageSourceProvider},
     * which does not change the default {@link StreamableMessageSource} for any {@link TenantDescriptor}.
     *
     * @param tenantProvider the default {@link TenantProvider} used to build {@link MultiTenantEventProcessor}s
     */
    public MultiTenantEventProcessingModule(TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.multiTenantDeadLetterQueueFactory = null;
        multiTenantStreamableMessageSourceProvider = ((defaultSource, processorName, tenantDescriptor, configuration) -> defaultSource);
    }

    /**
     * Initializes a {@link MultiTenantEventProcessingModule} with a default {@link TenantProvider} and a default {@link MultiTenantStreamableMessageSourceProvider},
     * which does not change the default {@link StreamableMessageSource} for any {@link TenantDescriptor}.
     *
     * @param tenantProvider the default {@link TenantProvider} used to build {@link MultiTenantEventProcessor}s
     */
    public MultiTenantEventProcessingModule(TenantProvider tenantProvider, MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory) {
        this.tenantProvider = tenantProvider;
        this.multiTenantDeadLetterQueueFactory = multiTenantDeadLetterQueueFactory;
        multiTenantStreamableMessageSourceProvider = ((defaultSource, processorName, tenantDescriptor, configuration) -> defaultSource);
    }

    /**
     * Initializes a {@link MultiTenantEventProcessingModule} with a default {@link TenantProvider} and a
     * {@link MultiTenantStreamableMessageSourceProvider}, which allows for the customization of the
     * {@link StreamableMessageSource} for each {@link TenantDescriptor}.
     *
     * @param tenantProvider                             the default {@link TenantProvider} used to build
     *                                                   {@link MultiTenantEventProcessor}s
     * @param multiTenantStreamableMessageSourceProvider the {@link MultiTenantStreamableMessageSourceProvider} used to
     *                                                   customize the {@link StreamableMessageSource} for each
     *                                                   {@link TenantDescriptor}
     * @param multiTenantDeadLetterQueueFactory
     */
    public MultiTenantEventProcessingModule(TenantProvider tenantProvider,
                                            MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider,
                                            MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory) {
        this.tenantProvider = tenantProvider;
        this.multiTenantDeadLetterQueueFactory = multiTenantDeadLetterQueueFactory;
        this.multiTenantStreamableMessageSourceProvider = multiTenantStreamableMessageSourceProvider;
    }

    private static String getName(String name, TenantDescriptor tenantDescriptor) {
        return name + "@" + tenantDescriptor.tenantId();
    }

    /**
     * @param name
     * @param tenantDescriptor
     * @return
     */
    public Optional<EventProcessor> eventProcessor(String name, TenantDescriptor tenantDescriptor) {
        return Optional.ofNullable(this.eventProcessors().get(getName(name, tenantDescriptor)));
    }

    @Override
    public Map<String, EventProcessor> eventProcessors() {
        Map<String, EventProcessor> original = super.eventProcessors();
        Map<String, EventProcessor> allProcessors = original.entrySet().stream()
                                                            .filter(entry -> entry.getValue().getClass()
                                                                                  .isAssignableFrom(
                                                                                          MultiTenantEventProcessor.class))
                                                            .flatMap(entry -> ((MultiTenantEventProcessor) entry.getValue()).tenantEventProcessors()
                                                                                                                            .stream())
                                                            .collect(Collectors.toMap(EventProcessor::getName, processor -> processor));
        allProcessors.putAll(original);
        return allProcessors;
    }

    @Override
    public EventProcessor subscribingEventProcessor(String name,
                                                    EventHandlerInvoker eventHandlerInvoker,
                                                    SubscribableMessageSource<? extends EventMessage<?>> messageSource) {
        MultiTenantEventProcessor eventProcessor = MultiTenantEventProcessor.builder()
                                                                            .name(name)
                                                                            .tenantSegmentFactory(
                                                                                    tenantDescriptor -> {
                                                                                        SubscribableMessageSource<? extends EventMessage<?>> source =
                                                                                                messageSource instanceof MultiTenantEventStore
                                                                                                        ? ((MultiTenantEventStore) messageSource).tenantSegment(tenantDescriptor)
                                                                                                        : messageSource;

                                                                                        return SubscribingEventProcessor.builder()
                                                                                                                        .name(getName(name, tenantDescriptor))
                                                                                                                        .eventHandlerInvoker(eventHandlerInvoker)
                                                                                                                        .rollbackConfiguration(super.rollbackConfiguration(name))
                                                                                                                        .errorHandler(super.errorHandler(name))
                                                                                                                        .messageMonitor(super.messageMonitor(
                                                                                                                                SubscribingEventProcessor.class,
                                                                                                                                name))
                                                                                                                        .messageSource(source)
                                                                                                                        .processingStrategy(DirectEventProcessingStrategy.INSTANCE)
                                                                                                                        .transactionManager(new TenantWrappedTransactionManager(
                                                                                                                                super.transactionManager(name),
                                                                                                                                tenantDescriptor))
                                                                                                                        .build();
                                                                                    })
                                                                            .build();

        tenantProvider.subscribe(eventProcessor);
        return eventProcessor;
    }

    @Override
    public EventProcessor trackingEventProcessor(String name,
                                                 EventHandlerInvoker eventHandlerInvoker,
                                                 TrackingEventProcessorConfiguration config,
                                                 StreamableMessageSource<TrackedEventMessage<?>> source) {
        MultiTenantEventProcessor eventProcessor = MultiTenantEventProcessor.builder()
                                                                            .name(name)
                                                                            .tenantSegmentFactory(
                                                                                    tenantDescriptor -> {
                                                                                        StreamableMessageSource<TrackedEventMessage<?>> tenantSource =
                                                                                                source instanceof MultiTenantEventStore
                                                                                                        ? ((MultiTenantEventStore) source).tenantSegment(tenantDescriptor)
                                                                                                        : source;

                                                                                        tenantSource = multiTenantStreamableMessageSourceProvider.build(
                                                                                                tenantSource,
                                                                                                name,
                                                                                                tenantDescriptor,
                                                                                                configuration
                                                                                        );
                                                                                        return TrackingEventProcessor.builder()
                                                                                                                     .name(getName(name, tenantDescriptor))
                                                                                                                     .eventHandlerInvoker(eventHandlerInvoker)
                                                                                                                     .rollbackConfiguration(super.rollbackConfiguration(name))
                                                                                                                     .errorHandler(super.errorHandler(name))
                                                                                                                     .messageMonitor(super.messageMonitor(TrackingEventProcessor.class,
                                                                                                                                                          name))
                                                                                                                     .messageSource(tenantSource)
                                                                                                                     .tokenStore(super.tokenStore(name))
                                                                                                                     .transactionManager(new TenantWrappedTransactionManager(super.transactionManager(
                                                                                                                             name), tenantDescriptor))
                                                                                                                     .trackingEventProcessorConfiguration(config)
                                                                                                                     .build();
                                                                                    }
                                                                            )
                                                                            .build();

        tenantProvider.subscribe(eventProcessor);

        return eventProcessor;
    }

    @Override
    public EventProcessor pooledStreamingEventProcessor(
            String name,
            EventHandlerInvoker eventHandlerInvoker,
            Configuration config,
            StreamableMessageSource<TrackedEventMessage<?>> source,
            PooledStreamingProcessorConfiguration processorConfiguration
    ) {

        MultiTenantEventProcessor eventProcessor = MultiTenantEventProcessor.builder()
                                                                            .name(name)
                                                                            .tenantSegmentFactory(
                                                                                    tenantDescriptor -> {
                                                                                        StreamableMessageSource<TrackedEventMessage<?>> tenantSource =
                                                                                                source instanceof MultiTenantEventStore
                                                                                                        ? ((MultiTenantEventStore) source).tenantSegment(tenantDescriptor)
                                                                                                        : source;

                                                                                        tenantSource = multiTenantStreamableMessageSourceProvider.build(
                                                                                                tenantSource,
                                                                                                name,
                                                                                                tenantDescriptor,
                                                                                                configuration
                                                                                        );

                                                                                        PooledStreamingEventProcessor.Builder builder =
                                                                                                PooledStreamingEventProcessor.builder()
                                                                                                                             .name(getName(name, tenantDescriptor))
                                                                                                                             .eventHandlerInvoker(eventHandlerInvoker)
                                                                                                                             .rollbackConfiguration(super.rollbackConfiguration(name))
                                                                                                                             .errorHandler(super.errorHandler(name))
                                                                                                                             .messageMonitor(super.messageMonitor(
                                                                                                                                     PooledStreamingEventProcessor.class,
                                                                                                                                     name))
                                                                                                                             .messageSource(tenantSource)
                                                                                                                             .tokenStore(super.tokenStore(name))
                                                                                                                             .transactionManager(new TenantWrappedTransactionManager(
                                                                                                                                     super.transactionManager(name),
                                                                                                                                     tenantDescriptor))
                                                                                                                             .coordinatorExecutor(processorName -> {
                                                                                                                                 ScheduledExecutorService coordinatorExecutor =
                                                                                                                                         defaultExecutor("Coordinator[" + processorName + "]");
                                                                                                                                 config.onShutdown(coordinatorExecutor::shutdown);
                                                                                                                                 return coordinatorExecutor;
                                                                                                                             })
                                                                                                                             .workerExecutor(processorName -> {
                                                                                                                                 ScheduledExecutorService workerExecutor =
                                                                                                                                         defaultExecutor("WorkPackage[" + processorName + "]");
                                                                                                                                 config.onShutdown(workerExecutor::shutdown);
                                                                                                                                 return workerExecutor;
                                                                                                                             });
                                                                                        return defaultPooledStreamingProcessorConfiguration.andThen(psepConfigs.getOrDefault(name, PooledStreamingProcessorConfiguration.noOp()))
                                                                                                                                           .andThen(processorConfiguration)
                                                                                                                                           .apply(config, builder)
                                                                                                                                           .build();
                                                                                    }
                                                                            )
                                                                            .build();

        tenantProvider.subscribe(eventProcessor);
        return eventProcessor;
    }

    /**
     * Registers a {@link MultiTenantDeadLetterQueue} for the given {@code processingGroup}. The given {@code queueBuilder}
     * Overrides user defined queue builder and puts the {@link SequencedDeadLetterQueue} in a {@link MultiTenantDeadLetterQueue}.
     *
     * @param processingGroup A {@link String} specifying the name of the processing group to register the given
     *                        {@link SequencedDeadLetterQueue} for.
     * @param queueBuilder    A builder method returning a {@link SequencedDeadLetterQueue} based on a
     *                        {@link Configuration}. The outcome is used by the given {@code processingGroup} to enqueue
     *                        and evaluate failed events in.
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    @Override
    public EventProcessingConfigurer registerDeadLetterQueue(String processingGroup,
                                                             Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>> queueBuilder) {
        if (multiTenantDeadLetterQueueFactory == null) {
            throw new AxonConfigurationException("Cannot register a DeadLetterQueue without a MultiTenantDeadLetterQueueFactory");
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
     * It is necessary to invoke {@code forTenant} method on the returned {@link SequencedDeadLetterProcessor}
     * to specify the tenant for which the dead letter should be processed.
     *
     * @param processingGroup The name of the processing group to register the {@link SequencedDeadLetterProcessor} for.
     */
    @Override
    public Optional<SequencedDeadLetterProcessor<EventMessage<?>>> sequencedDeadLetterProcessor(
            String processingGroup) {
        return super.sequencedDeadLetterProcessor(processingGroup)
                .map(MultiTenantDeadLetterProcessor::new);
    }

    private ScheduledExecutorService defaultExecutor(String factoryName) {
        return Executors.newScheduledThreadPool(1, new AxonThreadFactory(factoryName));
    }
}
