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

import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.connector.control.ControlChannel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.processor.EventProcessorControlService;
import org.axonframework.common.Registration;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.StartHandler;

import java.util.Map;

/**
 * Multi-tenant implementation of {@link EventProcessorControlService}.
 * <p>
 * Enables event processor control for multi-tenant environment in Axon Server dashboard.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class MultiTenantEventProcessorControlService
        extends EventProcessorControlService
        implements MultiTenantAwareComponent {

    /**
     * Initialize a {@link MultiTenantEventProcessorControlService}.
     * <p>
     * This service adds processor instruction handlers to the {@link ControlChannel} of the given {@code context}, for
     * every tenant. Doing so ensures operation like the {@link EventProcessor#start() start} and
     * {@link EventProcessor#shutDown() shutdown} can be triggered through Axon Server. Furthermore, it sets the
     * configured load balancing strategies through the {@link AdminChannel}  of the {@code context}.
     *
     * @param axonServerConnectionManager  A {@link AxonServerConnectionManager} from which to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel}.
     * @param eventProcessingConfiguration The {@link EventProcessor} configuration of this application, used to
     *                                     retrieve the registered event processors from.
     * @param axonServerConfiguration      The {@link AxonServerConfiguration} used to retrieve the
     *                                     {@link AxonServerConnectionManager#getDefaultContext() default context}
     *                                     from.
     */
    public MultiTenantEventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                                   EventProcessingConfiguration eventProcessingConfiguration,
                                                   AxonServerConfiguration axonServerConfiguration) {
        this(axonServerConnectionManager,
             eventProcessingConfiguration,
             axonServerConfiguration.getContext(),
             axonServerConfiguration.getEventProcessorConfiguration().getProcessors());
    }

    /**
     * Initialize a {@link MultiTenantEventProcessorControlService}.
     * <p>
     * This service adds processor instruction handlers to the {@link ControlChannel} of the given {@code context}, for
     * every tenant. Doing so ensures operation like the {@link EventProcessor#start() start} and
     * {@link EventProcessor#shutDown() shutdown} can be triggered through Axon Server. Furthermore, it sets the
     * configured load balancing strategies through the {@link AdminChannel}  of the {@code context}.
     *
     * @param axonServerConnectionManager  A {@link AxonServerConnectionManager} from which to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel}.
     * @param eventProcessingConfiguration The {@link EventProcessor} configuration of this application, used to
     *                                     retrieve the registered event processors from.
     * @param context                      The context of this application instance to retrieve the
     *                                     {@link ControlChannel} and {@link AdminChannel} for.
     * @param processorConfig              The processor configuration from the {@link AxonServerConfiguration}, used to
     *                                     (for example) retrieve the load balancing strategies from.
     */
    public MultiTenantEventProcessorControlService(
            AxonServerConnectionManager axonServerConnectionManager,
            EventProcessingConfiguration eventProcessingConfiguration,
            String context,
            Map<String, AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings> processorConfig
    ) {
        super(axonServerConnectionManager, eventProcessingConfiguration, context, processorConfig);
    }

    @StartHandler(phase = Phase.INSTRUCTION_COMPONENTS)
    @Override
    public void start() {
        if (axonServerConnectionManager == null || eventProcessingConfiguration == null) {
            return;
        }

        Map<String, EventProcessor> eventProcessors = eventProcessingConfiguration.eventProcessors();
        eventProcessors.forEach((name, processor) -> {
            if (processor instanceof MultiTenantEventProcessor) {
                return;
            }
            String context = name.substring(name.indexOf("@") + 1);
            ControlChannel controlChannel = axonServerConnectionManager.getConnection(context)
                                                                       .controlChannel();
            AxonProcessorInstructionHandler instructionHandler = new AxonProcessorInstructionHandler(processor, name);
            controlChannel.registerEventProcessor(name, infoSupplier(processor), instructionHandler);
        });
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        //Already registered
        return () -> true;
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        if (axonServerConnectionManager == null || eventProcessingConfiguration == null) {
            return () -> true;
        }
        Map<String, EventProcessor> eventProcessors = eventProcessingConfiguration.eventProcessors();
        eventProcessors.forEach((name, processor) -> {
            if (processor instanceof MultiTenantEventProcessor || !name.contains(tenantDescriptor.tenantId())) {
                return;
            }
            ControlChannel controlChannel = axonServerConnectionManager.getConnection(tenantDescriptor.tenantId())
                                                                       .controlChannel();
            AxonProcessorInstructionHandler instructionHandler = new AxonProcessorInstructionHandler(processor, name);
            controlChannel.registerEventProcessor(name, infoSupplier(processor), instructionHandler);
        });
        return () -> true;
    }
}
