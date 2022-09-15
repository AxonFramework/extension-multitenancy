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

import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
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

    @BeforeEach
    void setUp() {
        configurer = DefaultConfigurer.defaultConfiguration();
        multiTenantEventProcessor = mock(MultiTenantEventProcessor.class);
    }

    @Test
    public void testEventProcessors() {
        Map<String, MultiTenantEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();

        TenantProvider tenantProvider = mock(TenantProvider.class);

        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider));
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
        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider));
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
    public void subscribingEventProcessor() {
        SubscribableMessageSource<EventMessage<?>> mockedSource = mock(SubscribableMessageSource.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider));

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
        configurer.registerModule(new MultiTenantEventProcessingModule(tenantProvider));
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
}