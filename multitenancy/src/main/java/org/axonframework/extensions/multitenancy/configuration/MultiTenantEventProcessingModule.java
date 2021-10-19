package org.axonframework.extensions.multitenancy.configuration;

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.config.Configuration;
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
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * @author Stefan Dragisic
 */
public class MultiTenantEventProcessingModule extends EventProcessingModule {

    private final TenantProvider tenantProvider;

    public MultiTenantEventProcessingModule(TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
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
                .filter(entry -> entry.getValue().getClass().isAssignableFrom(MultiTenantEventProcessor.class))
                .flatMap(entry -> ((MultiTenantEventProcessor) entry.getValue()).tenantSegments().stream())
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
                                    .messageMonitor(super.messageMonitor(SubscribingEventProcessor.class, name))
                                    .messageSource(source)
                                    .processingStrategy(DirectEventProcessingStrategy.INSTANCE)
                                    .transactionManager(super.transactionManager(name))
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
                            StreamableMessageSource<TrackedEventMessage<?>> messageSource =
                                    source instanceof MultiTenantEventStore
                                            ? ((MultiTenantEventStore) source).tenantSegment(tenantDescriptor)
                                            : source;
                            return TrackingEventProcessor.builder()
                                    .name(getName(name, tenantDescriptor))
                                    .eventHandlerInvoker(eventHandlerInvoker)
                                    .rollbackConfiguration(super.rollbackConfiguration(name))
                                    .errorHandler(super.errorHandler(name))
                                    .messageMonitor(super.messageMonitor(TrackingEventProcessor.class, name))
                                    .messageSource(messageSource)
                                    .tokenStore(super.tokenStore(name)) //todo-token store factory
                                    .transactionManager(super.transactionManager(name))
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
            StreamableMessageSource<TrackedEventMessage<?>> messageSource,
            PooledStreamingProcessorConfiguration processorConfiguration
    ) {

        MultiTenantEventProcessor eventProcessor = MultiTenantEventProcessor.builder()
                .name(name)
                .tenantSegmentFactory(
                        tenantDescriptor -> {
                            StreamableMessageSource<TrackedEventMessage<?>> source =
                                    messageSource instanceof MultiTenantEventStore
                                            ? ((MultiTenantEventStore) messageSource).tenantSegment(tenantDescriptor)
                                            : messageSource;

                            PooledStreamingEventProcessor.Builder builder =
                                    PooledStreamingEventProcessor.builder()
                                            .name(getName(name, tenantDescriptor))
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .rollbackConfiguration(super.rollbackConfiguration(name))
                                            .errorHandler(super.errorHandler(name))
                                            .messageMonitor(super.messageMonitor(PooledStreamingEventProcessor.class, name))
                                            .messageSource(source)
                                            .tokenStore(super.tokenStore(name))
                                            .transactionManager(super.transactionManager(name))
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

    private ScheduledExecutorService defaultExecutor(String factoryName) {
        return Executors.newScheduledThreadPool(1, new AxonThreadFactory(factoryName));
    }
}
