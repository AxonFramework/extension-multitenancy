package org.axonframework.extensions.multitenancy.autoconfig;

import io.axoniq.axonserver.connector.control.ControlChannel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.processor.EventProcessorControlService;
import org.axonframework.common.Registration;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.eventhandeling.MultiTenantEventProcessor;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.StartHandler;

/**
 * @author Stefan Dragisic
 */
public class MultiTenantEventProcessorControlService extends EventProcessorControlService
        implements MultiTenantAwareComponent {

    public MultiTenantEventProcessorControlService(
            AxonServerConnectionManager axonServerConnectionManager,
            EventProcessingConfiguration eventProcessingConfiguration,
            AxonServerConfiguration axonServerConfiguration) {
        super(axonServerConnectionManager, eventProcessingConfiguration, "");
    }

    public MultiTenantEventProcessorControlService(
            AxonServerConnectionManager axonServerConnectionManager,
            EventProcessingConfiguration eventProcessingConfiguration, String context) {
        super(axonServerConnectionManager, eventProcessingConfiguration, "");
    }

    @StartHandler(phase = Phase.INSTRUCTION_COMPONENTS)
    @Override
    public void start() {
        if (axonServerConnectionManager != null && eventProcessingConfiguration != null) {
            eventProcessingConfiguration.eventProcessors()
                                        .forEach(
                                                (name, processor) -> {
                                                    if (processor instanceof MultiTenantEventProcessor) {
                                                        return;
                                                    }
                                                    String ctx = name.substring(name.lastIndexOf("@") + 1);
                                                    ControlChannel ch = axonServerConnectionManager.getConnection(ctx)
                                                                                                   .controlChannel();
                                                    ch.registerEventProcessor(name,
                                                                              infoSupplier(processor),
                                                                              new AxonProcessorInstructionHandler(
                                                                                      processor,
                                                                                      name));
                                                });
        }
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        //Already registered
        return () -> true;
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        if (axonServerConnectionManager != null && eventProcessingConfiguration != null) {
            eventProcessingConfiguration.eventProcessors()
                                        .forEach(
                                                (name, processor) -> {
                                                    ControlChannel ch = axonServerConnectionManager.getConnection(
                                                                                                           tenantDescriptor.tenantId())
                                                                                                   .controlChannel();
                                                    ch.registerEventProcessor(
                                                            name,
                                                            infoSupplier(processor),
                                                            new AxonProcessorInstructionHandler(
                                                                    processor,
                                                                    name));
                                                });
        }

        return () -> true;
    }
}
