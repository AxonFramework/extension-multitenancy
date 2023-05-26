/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.Registration;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterProcessor;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterQueue;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterQueueFactory;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class MultiTenantEventProcessingModuleTest {


    private Configurer configurer;
    private MultiTenantEventProcessor multiTenantEventProcessor;

    private MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider;

    private MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory;

    @BeforeEach
    void setUp() {
        configurer = DefaultConfigurer.defaultConfiguration();
        multiTenantEventProcessor = mock(MultiTenantEventProcessor.class);
        multiTenantStreamableMessageSourceProvider =
                (defaultSource, processorName, tenantDescriptor, configuration) -> defaultSource;
        multiTenantDeadLetterQueueFactory = mock(MultiTenantDeadLetterQueueFactory.class);
    }

    @Test
    public void testDeadLetterQueue() {
        Map<String, MultiTenantDeadLetterQueue<EventMessage<?>>> multiTenantDeadLetterQueueMap = new ConcurrentHashMap<>();
        TenantProvider tenantProvider = mock(TenantProvider.class);

        multiTenantDeadLetterQueueFactory = (processingGroup) -> multiTenantDeadLetterQueueMap.computeIfAbsent(processingGroup, (key) -> {
            MultiTenantDeadLetterQueue<EventMessage<?>> deadLetterQueue  = MultiTenantDeadLetterQueue.builder()
                                                                                                     .targetTenantResolver(
                                                                                                             mock(TargetTenantResolver.class))
                                                                                                     .processingGroup(processingGroup)
                                                                                                     .build();
            return deadLetterQueue;
        });

        SequencedDeadLetterQueue originalDeadLetterQueue = mock(SequencedDeadLetterQueue.class);


        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider, multiTenantDeadLetterQueueFactory));
        configurer.eventProcessing()
                  .registerDeadLetterQueue("dlq", c ->  originalDeadLetterQueue);

        MultiTenantDeadLetterQueue<EventMessage<?>> multiTenantDeadLetterQueue = multiTenantDeadLetterQueueMap.get("dlq");

        multiTenantDeadLetterQueue.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));
        multiTenantDeadLetterQueue.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant2"));

        assertEquals(originalDeadLetterQueue, multiTenantDeadLetterQueue.getTenantSegment(TenantDescriptor.tenantWithId("tenant1")));
        assertEquals(originalDeadLetterQueue, multiTenantDeadLetterQueue.getTenantSegment(TenantDescriptor.tenantWithId("tenant2")));
    }

    @Test
    public void testSequencedDeadLetterProcessor() {
        multiTenantDeadLetterQueueFactory = (processingGroup) -> {
            MultiTenantDeadLetterQueue<EventMessage<?>> deadLetterQueue  = MultiTenantDeadLetterQueue.builder()
                                                                                                     .targetTenantResolver(
                                                                                                             mock(TargetTenantResolver.class))
                                                                                                     .processingGroup(processingGroup)
                                                                                                     .build();
            return deadLetterQueue;
        };

        configurer.registerModule(new MultiTenantEventProcessingModule(mock(TenantProvider.class), multiTenantDeadLetterQueueFactory));

        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .registerDeadLetterQueue("java.lang", c ->  mock(SequencedDeadLetterQueue.class))
                  .configureDefaultStreamableMessageSource(config ->  mock(StreamableMessageSource.class))
                  .registerEventHandler(c -> new Object()); // --> java.lang

        Configuration configuration = configurer.start();

        ArgumentCaptor<MultiTenantEventProcessor> sep = ArgumentCaptor.forClass(MultiTenantEventProcessor.class);
        sep.getAllValues()
           .forEach(ep -> {
                        ep.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));
                        ep.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant2"));
                    }
           );

        Optional<SequencedDeadLetterProcessor<EventMessage<?>>> deadLetterProcessor = configuration.eventProcessingConfiguration()
                                                                                                                        .sequencedDeadLetterProcessor(
                                                                                                                                "java.lang");

        assertTrue(deadLetterProcessor.isPresent());
        assertTrue(deadLetterProcessor.get() instanceof MultiTenantDeadLetterProcessor);
    }

    @Test
    public void testEventProcessors() {
        Map<String, MultiTenantEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();

        TenantProvider tenantProvider = mock(TenantProvider.class);

        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider, multiTenantDeadLetterQueueFactory));
        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, eventHandlerInvoker) -> {
                      processors.put(name, multiTenantEventProcessor);
                      return multiTenantEventProcessor;
                  })
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent
                  .registerEventHandler(c -> map); // --> java.util.concurrent
        Configuration configuration = configurer.start();

        assertEquals(2, configuration.eventProcessingConfiguration().eventProcessors().size());
    }


    @Test
    public void testTrackingEventProcessor() {
        StreamableMessageSource<TrackedEventMessage<?>> mockedSource = mock(StreamableMessageSource.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider, multiTenantDeadLetterQueueFactory));
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent
                  .registerTrackingEventProcessorConfiguration("tracking", config -> testTepConfig);
        Configuration configuration = configurer.start();

        ArgumentCaptor<MultiTenantEventProcessor> sep = ArgumentCaptor.forClass(MultiTenantEventProcessor.class);
        verify(tenantProvider, times(2)).subscribe(sep.capture());
        sep.getAllValues()
           .forEach(ep -> {
                        ep.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));
                        ep.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant2"));
                    }
           );

        assertEquals(6, configuration.eventProcessingConfiguration().eventProcessors().size());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent", MultiTenantEventProcessor.class).isPresent());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang", MultiTenantEventProcessor.class).isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent@tenant1", TrackingEventProcessor.class)
                                .isPresent());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang@tenant1", TrackingEventProcessor.class).isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent@tenant2", TrackingEventProcessor.class)
                                .isPresent());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang@tenant2", TrackingEventProcessor.class).isPresent());
    }

    @Test
    public void testTrackingEventProcessorCustomSource() {
        StreamableMessageSource<TrackedEventMessage<?>> defaultSource = mock(StreamableMessageSource.class);
        StreamableMessageSource<TrackedEventMessage<?>> customSource = mock(StreamableMessageSource.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider =
                (source, processorName, tenantDescriptor, configuration) -> customSource;

        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider,
                                                                       multiTenantStreamableMessageSourceProvider,
                                                                       null)); //todo
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> defaultSource)
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent
                  .registerTrackingEventProcessorConfiguration("tracking", config -> testTepConfig);
        Configuration configuration = configurer.start();

        ArgumentCaptor<MultiTenantEventProcessor> sep = ArgumentCaptor.forClass(MultiTenantEventProcessor.class);
        verify(tenantProvider, times(2)).subscribe(sep.capture());
        sep.getAllValues()
           .forEach(ep -> {
                        ep.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));
                        ep.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant2"));
                    }
           );

        assertEquals(6, configuration.eventProcessingConfiguration().eventProcessors().size());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent", MultiTenantEventProcessor.class).isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang", MultiTenantEventProcessor.class).isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent@tenant1", TrackingEventProcessor.class)
                                .isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent@tenant1", TrackingEventProcessor.class)
                                .map(TrackingEventProcessor::getMessageSource).map(it->it.equals(customSource)).orElse(false));

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent@tenant1", TrackingEventProcessor.class)
                                .map(TrackingEventProcessor::getMessageSource).map(it->it.equals(customSource)).orElse(false));

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang@tenant1", TrackingEventProcessor.class).isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang@tenant1", TrackingEventProcessor.class)
                                .map(TrackingEventProcessor::getMessageSource).map(it->it.equals(customSource)).orElse(false));

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent@tenant2", TrackingEventProcessor.class)
                                .isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.util.concurrent@tenant2", TrackingEventProcessor.class)
                                .map(TrackingEventProcessor::getMessageSource).map(it->it.equals(customSource)).orElse(false));

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang@tenant2", TrackingEventProcessor.class).isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("java.lang@tenant2", TrackingEventProcessor.class)
                                .map(TrackingEventProcessor::getMessageSource).map(it->it.equals(customSource)).orElse(false));
    }

    @Test
    public void subscribingEventProcessor() {
        SubscribableMessageSource<EventMessage<?>> mockedSource = mock(SubscribableMessageSource.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider, multiTenantDeadLetterQueueFactory));

        configurer.eventProcessing()
                  .usingSubscribingEventProcessors()
                  .configureDefaultSubscribableMessageSource(config -> mockedSource)
                  .byDefaultAssignTo("subscribing")
                  .registerSubscribingEventProcessor("subscribing", config -> mockedSource)
                  .registerEventHandler(config -> new Object());
        Configuration configuration = configurer.start();

        ArgumentCaptor<MultiTenantEventProcessor> sep = ArgumentCaptor.forClass(MultiTenantEventProcessor.class);
        verify(tenantProvider).subscribe(sep.capture());
        sep.getValue().registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));

        assertEquals(2, configuration.eventProcessingConfiguration().eventProcessors().size());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("subscribing", MultiTenantEventProcessor.class).isPresent());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("subscribing@tenant1", SubscribingEventProcessor.class).isPresent());
    }

    @Test
    public void pooledStreamingEventProcessor() {
        StreamableMessageSource<TrackedEventMessage<?>> mockedSource = mock(StreamableMessageSource.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider, multiTenantDeadLetterQueueFactory));
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> new Object())
                  .registerTrackingEventProcessorConfiguration("tracking", config -> testTepConfig);
        Configuration configuration = configurer.start();

        ArgumentCaptor<MultiTenantEventProcessor> sep = ArgumentCaptor.forClass(MultiTenantEventProcessor.class);
        verify(tenantProvider).subscribe(sep.capture());
        sep.getValue().registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));

        assertEquals(2, configuration.eventProcessingConfiguration().eventProcessors().size());
        Optional<MultiTenantEventProcessor> resultTrackingTep =
                configuration.eventProcessingConfiguration().eventProcessor("default", MultiTenantEventProcessor.class);
        assertTrue(resultTrackingTep.isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("default", MultiTenantEventProcessor.class).isPresent());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("default@tenant1", PooledStreamingEventProcessor.class).isPresent());
    }

    @Test
    public void pooledStreamingEventProcessorCustomSource() {
        StreamableMessageSource<TrackedEventMessage<?>> mockedSource = mock(StreamableMessageSource.class);

        StreamableMessageSource<TrackedEventMessage<?>> customSource = mock(StreamableMessageSource.class);

        MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider =
                (source, processorName, tenantDescriptor, configuration) -> customSource;

        TenantProvider tenantProvider = mock(TenantProvider.class);
        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider,
                                                                       multiTenantStreamableMessageSourceProvider,
                                                                       multiTenantDeadLetterQueueFactory));
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> new Object())
                  .registerTrackingEventProcessorConfiguration("tracking", config -> testTepConfig);
        Configuration configuration = configurer.start();

        ArgumentCaptor<MultiTenantEventProcessor> sep = ArgumentCaptor.forClass(MultiTenantEventProcessor.class);
        verify(tenantProvider).subscribe(sep.capture());
        sep.getValue().registerAndStartTenant(TenantDescriptor.tenantWithId("tenant1"));

        assertEquals(2, configuration.eventProcessingConfiguration().eventProcessors().size());
        Optional<MultiTenantEventProcessor> resultTrackingTep =
                configuration.eventProcessingConfiguration().eventProcessor("default", MultiTenantEventProcessor.class);
        assertTrue(resultTrackingTep.isPresent());

        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("default", MultiTenantEventProcessor.class).isPresent());
        assertTrue(configuration.eventProcessingConfiguration()
                                .eventProcessor("default@tenant1", PooledStreamingEventProcessor.class).isPresent());

        when(customSource.openStream(any())).thenReturn(mock(BlockingStream.class));
        when(customSource.createHeadToken()).thenReturn(mock(TrackingToken.class));

        configuration.eventProcessingConfiguration()
                     .eventProcessor("default@tenant1",
                                     PooledStreamingEventProcessor.class)
                     .ifPresent(pooledStreamingEventProcessor -> {
                         pooledStreamingEventProcessor.shutDown();
                         pooledStreamingEventProcessor.resetTokens(StreamableMessageSource::createHeadToken);
                     });


        verify(customSource).createHeadToken();
    }
}