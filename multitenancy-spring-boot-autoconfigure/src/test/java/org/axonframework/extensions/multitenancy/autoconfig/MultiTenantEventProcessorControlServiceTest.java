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

package org.axonframework.extensions.multitenancy.autoconfig;

import com.google.common.collect.ImmutableMap;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.control.ControlChannel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class MultiTenantEventProcessorControlServiceTest {

    AxonServerConnectionManager axonServerConnectionManager;
    EventProcessingConfiguration eventProcessingConfiguration;
    AxonServerConfiguration axonServerConfiguration;
    private MultiTenantEventProcessorControlService testSubject;

    @BeforeEach
    void setUp() {
        axonServerConnectionManager = mock(AxonServerConnectionManager.class);
        eventProcessingConfiguration = mock(EventProcessingConfiguration.class);
        axonServerConfiguration = mock(AxonServerConfiguration.class);
        testSubject = new MultiTenantEventProcessorControlService(axonServerConnectionManager,
                                                                  eventProcessingConfiguration,
                                                                  axonServerConfiguration);
    }

    @Test
    public void testStart() {
        AxonServerConnection connectionTenant1 = mock(AxonServerConnection.class);
        ControlChannel controlTenant1 = mock(ControlChannel.class);
        when(connectionTenant1.controlChannel()).thenReturn(controlTenant1);
        AxonServerConnection connectionTenant2 = mock(AxonServerConnection.class);
        ControlChannel controlTenant2 = mock(ControlChannel.class);
        when(connectionTenant2.controlChannel()).thenReturn(controlTenant2);

        ArgumentCaptor<String> contextCapture = ArgumentCaptor.forClass(String.class);
        when(axonServerConnectionManager.getConnection(contextCapture.capture())).thenAnswer(a -> {
            if (contextCapture.getValue().equals("tenant-1")) {
                return connectionTenant1;
            } else {
                return connectionTenant2;
            }
        });

        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                "tep@tenant-1",
                mock(EventProcessor.class),
                "tep@tenant-2",
                mock(EventProcessor.class),
                "proxy-ep",
                mock(MultiTenantEventProcessor.class)
        ));

        testSubject.start();

        verify(controlTenant1).registerEventProcessor(eq("tep@tenant-1"), any(), any());
        verify(controlTenant2).registerEventProcessor(eq("tep@tenant-2"), any(), any());
    }

    @Test
    public void testAddingNewTenantAfterStart() {
        AxonServerConnection connectionTenant1 = mock(AxonServerConnection.class);
        ControlChannel controlTenant1 = mock(ControlChannel.class);
        when(connectionTenant1.controlChannel()).thenReturn(controlTenant1);
        AxonServerConnection connectionTenant2 = mock(AxonServerConnection.class);
        ControlChannel controlTenant2 = mock(ControlChannel.class);
        when(connectionTenant2.controlChannel()).thenReturn(controlTenant2);

        ArgumentCaptor<String> contextCapture = ArgumentCaptor.forClass(String.class);
        when(axonServerConnectionManager.getConnection(contextCapture.capture())).thenAnswer(a -> {
            if (contextCapture.getValue().equals("tenant-1")) {
                return connectionTenant1;
            } else {
                return connectionTenant2;
            }
        });

        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                "tep@tenant-1",
                mock(EventProcessor.class),
                "proxy-ep",
                mock(MultiTenantEventProcessor.class)
        ));

        testSubject.start();

        verify(controlTenant1).registerEventProcessor(eq("tep@tenant-1"), any(), any());
        verify(controlTenant2, times(0)).registerEventProcessor(eq("tep@tenant-2"), any(), any());

        when(eventProcessingConfiguration.eventProcessors()).thenReturn(ImmutableMap.of(
                "tep@tenant-1",
                mock(EventProcessor.class),
                "tep@tenant-2",
                mock(EventProcessor.class),
                "proxy-ep",
                mock(MultiTenantEventProcessor.class)
        ));

        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("tenant-2"));
        verify(controlTenant2).registerEventProcessor(eq("tep@tenant-2"), any(), any());
    }
}