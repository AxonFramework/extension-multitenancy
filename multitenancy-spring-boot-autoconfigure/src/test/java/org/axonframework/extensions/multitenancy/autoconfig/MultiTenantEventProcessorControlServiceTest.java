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

package org.axonframework.extensions.multitenancy.autoconfig;

import com.google.common.collect.ImmutableMap;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.connector.control.ControlChannel;
import io.axoniq.axonserver.grpc.admin.Admin;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantEventProcessorControlService}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantEventProcessorControlServiceTest {

    private static final String PROCESSOR_NAME = "some-processor";
    private static final String TOKEN_STORE_IDENTIFIER = "token-store-identifier";
    private static final String LOAD_BALANCING_STRATEGY = "some-strategy";

    private AxonServerConnectionManager axonServerConnectionManager;
    private EventProcessingConfiguration eventProcessingConfiguration;

    private ControlChannel controlTenant1;
    private ControlChannel controlAdmin;

    private AdminChannel adminAdmin;
    private AdminChannel adminTenant1;
    private ControlChannel controlTenant2;
    private AdminChannel adminTenant2;

    private MultiTenantEventProcessorControlService testSubject;

    @BeforeEach
    void setUp() {
        axonServerConnectionManager = mock(AxonServerConnectionManager.class);
        mockConnectionManager();

        eventProcessingConfiguration = mock(EventProcessingConfiguration.class);
        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier()).thenReturn(Optional.of(TOKEN_STORE_IDENTIFIER));
        when(eventProcessingConfiguration.tokenStore(PROCESSOR_NAME)).thenReturn(tokenStore);

        AxonServerConfiguration axonServerConfig = mock(AxonServerConfiguration.class);
        mockAxonServerConfig(axonServerConfig);

        testSubject = new MultiTenantEventProcessorControlService(axonServerConnectionManager,
                                                                  eventProcessingConfiguration,
                                                                  axonServerConfig);
    }

    private void mockConnectionManager() {
        AxonServerConnection connectionAdmin = mock(AxonServerConnection.class);
        controlAdmin = mock(ControlChannel.class);
        adminAdmin = mock(AdminChannel.class);
        when(connectionAdmin.controlChannel()).thenReturn(controlAdmin);
        when(connectionAdmin.adminChannel()).thenReturn(adminAdmin);
        when(adminAdmin.loadBalanceEventProcessor(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(adminAdmin.setAutoLoadBalanceStrategy(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        AxonServerConnection connectionTenant1 = mock(AxonServerConnection.class);
        controlTenant1 = mock(ControlChannel.class);
        when(connectionTenant1.controlChannel()).thenReturn(controlTenant1);
        adminTenant1 = mock(AdminChannel.class);
        when(adminTenant1.loadBalanceEventProcessor(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(adminTenant1.setAutoLoadBalanceStrategy(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(connectionTenant1.adminChannel()).thenReturn(adminTenant1);
        AxonServerConnection connectionTenant2 = mock(AxonServerConnection.class);
        controlTenant2 = mock(ControlChannel.class);
        when(connectionTenant2.controlChannel()).thenReturn(controlTenant2);
        adminTenant2 = mock(AdminChannel.class);
        when(adminTenant2.loadBalanceEventProcessor(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(adminTenant2.setAutoLoadBalanceStrategy(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(connectionTenant2.adminChannel()).thenReturn(adminTenant2);
        ArgumentCaptor<String> contextCapture = ArgumentCaptor.forClass(String.class);
        when(axonServerConnectionManager.getConnection(contextCapture.capture()))
                .thenAnswer(a -> {
                    String capturedValue = contextCapture.getValue();
                    if (capturedValue.equals("tenant-1")) {
                        return connectionTenant1;
                    } else if (capturedValue.equals("tenant-2")) {
                        return connectionTenant2;
                    } else if (capturedValue.equals("_admin")) {
                        return connectionAdmin;
                    } else {
                        throw new IllegalArgumentException("Unexpected value: " + capturedValue);
                    }
                });
    }

    private static void mockAxonServerConfig(AxonServerConfiguration axonServerConfig) {
        Map<String, AxonServerConfiguration.Eventhandling.ProcessorSettings> processorSettings = new HashMap<>();
        AxonServerConfiguration.Eventhandling eventHandling = mock(AxonServerConfiguration.Eventhandling.class);
        AxonServerConfiguration.Eventhandling.ProcessorSettings tepSettings =
                new AxonServerConfiguration.Eventhandling.ProcessorSettings();
        tepSettings.setLoadBalancingStrategy(LOAD_BALANCING_STRATEGY);
        tepSettings.setAutomaticBalancing(true);
        processorSettings.put(PROCESSOR_NAME, tepSettings);
        when(eventHandling.getProcessors()).thenReturn(processorSettings);
        when(axonServerConfig.getEventhandling()).thenReturn(eventHandling);
    }

    @Test
    void registersInstructionHandlersWithEachContextControlChannelOnStart() {
        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                PROCESSOR_NAME + "@tenant-1", mock(EventProcessor.class),
                PROCESSOR_NAME + "@tenant-2", mock(EventProcessor.class),
                "proxy-ep", mock(MultiTenantEventProcessor.class)
        ));

        testSubject.start();

        verify(controlTenant1).registerEventProcessor(eq(PROCESSOR_NAME + "@tenant-1"), any(), any());
        verify(controlTenant2).registerEventProcessor(eq(PROCESSOR_NAME + "@tenant-2"), any(), any());
    }

    @Test
    void registersInstructionHandlersWithoutTenantOnStart() {
        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                PROCESSOR_NAME , mock(EventProcessor.class)
        ));

        testSubject.start();

        verify(controlAdmin).registerEventProcessor(eq(PROCESSOR_NAME), any(), any());
    }

    @Test
    void addingNewTenantAfterStart() {
        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                PROCESSOR_NAME + "@tenant-1", mock(EventProcessor.class),
                "proxy-ep", mock(MultiTenantEventProcessor.class)
        ));

        testSubject.start();

        verify(controlTenant1).registerEventProcessor(eq(PROCESSOR_NAME + "@tenant-1"), any(), any());
        verify(controlTenant2, times(0)).registerEventProcessor(eq(PROCESSOR_NAME + "@tenant-2"), any(), any());

        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                PROCESSOR_NAME + "@tenant-1", mock(EventProcessor.class),
                PROCESSOR_NAME + "@tenant-2", mock(EventProcessor.class),
                "proxy-ep", mock(MultiTenantEventProcessor.class)
        ));

        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant-2"));
        verify(controlTenant2).registerEventProcessor(eq(PROCESSOR_NAME + "@tenant-2"), any(), any());
    }

    @Test
    void willSetLoadBalancingStrategyForProcessorsWithPropertiesOnStart() {
        String processorNameWithoutSettings = "processor-without-load-balancing";
        String expectedStrategy = "some-strategy";

        // Given
        // Mock Event Processor Configuration
        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                PROCESSOR_NAME + "@tenant-1", mock(EventProcessor.class),
                PROCESSOR_NAME + "@tenant-2", mock(EventProcessor.class),
                processorNameWithoutSettings + "@tenant-1", mock(EventProcessor.class),
                processorNameWithoutSettings + "@tenant-2", mock(EventProcessor.class),
                "proxy-ep", mock(MultiTenantEventProcessor.class)
        ));

        // When
        testSubject.start();

        // Then
        // Registers instruction handlers
        verify(controlTenant1).registerEventProcessor(eq(PROCESSOR_NAME + "@tenant-1"), any(), any());
        verify(controlTenant2).registerEventProcessor(eq(PROCESSOR_NAME + "@tenant-2"), any(), any());
        verify(controlTenant1).registerEventProcessor(eq(processorNameWithoutSettings + "@tenant-1"), any(), any());
        verify(controlTenant2).registerEventProcessor(eq(processorNameWithoutSettings + "@tenant-2"), any(), any());
        // Load balances Processors
        verify(adminTenant1).loadBalanceEventProcessor(PROCESSOR_NAME, TOKEN_STORE_IDENTIFIER, expectedStrategy);
        verify(adminTenant2).loadBalanceEventProcessor(PROCESSOR_NAME, TOKEN_STORE_IDENTIFIER, expectedStrategy);
        verify(adminTenant1, never()).loadBalanceEventProcessor(eq(processorNameWithoutSettings), any(), any());
        verify(adminTenant2, never()).loadBalanceEventProcessor(eq(processorNameWithoutSettings), any(), any());
        // Enables automatic load balancing
        verify(adminTenant1).setAutoLoadBalanceStrategy(PROCESSOR_NAME, TOKEN_STORE_IDENTIFIER, expectedStrategy);
        verify(adminTenant2).setAutoLoadBalanceStrategy(PROCESSOR_NAME, TOKEN_STORE_IDENTIFIER, expectedStrategy);
        verify(adminTenant1, never()).setAutoLoadBalanceStrategy(eq(processorNameWithoutSettings), any(), any());
        verify(adminTenant2, never()).setAutoLoadBalanceStrategy(eq(processorNameWithoutSettings), any(), any());
    }
}