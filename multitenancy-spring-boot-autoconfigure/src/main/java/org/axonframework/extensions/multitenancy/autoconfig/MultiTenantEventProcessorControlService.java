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

import io.axoniq.axonserver.connector.AxonServerConnection;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

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

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
             axonServerConfiguration.getEventhandling().getProcessors());
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
            Map<String, AxonServerConfiguration.Eventhandling.ProcessorSettings> processorConfig
    ) {
        super(axonServerConnectionManager, eventProcessingConfiguration, context, processorConfig);
    }

    @StartHandler(phase = Phase.INSTRUCTION_COMPONENTS)
    @Override
    public void start() {
        if (axonServerConnectionManager == null || eventProcessingConfiguration == null) {
            return;
        }

        Map<String, AxonServerConnection> contextToConnection = new HashMap<>();
        Map<String, EventProcessor> eventProcessors = eventProcessingConfiguration.eventProcessors();
        Map<String, String> strategiesPerProcessor = strategiesPerProcessor(eventProcessors);

        eventProcessors.forEach((processorAndContext, processor) -> {
            if (processor instanceof MultiTenantEventProcessor) {
                return;
            }

            String processorName = processorNameFromCombination(processorAndContext);
            String context = contextFromCombination(processorAndContext);
            AxonServerConnection connection =
                    contextToConnection.computeIfAbsent(context, axonServerConnectionManager::getConnection);

            registerInstructionHandler(connection.controlChannel(), processorAndContext, processor);
            String strategyForProcessor = strategiesPerProcessor.get(processorName);
            if (strategyForProcessor != null) {
                setLoadBalancingStrategy(connection.adminChannel(), processorName, strategyForProcessor);
            }
        });
    }

    private Map<String, String> strategiesPerProcessor(Map<String, EventProcessor> eventProcessors) {
        List<String> processorNames =
                eventProcessors.entrySet()
                               .stream()
                               // Filter out MultiTenantEventProcessors as those aren't registered with Axon Server anyhow.
                               .filter(entry -> !(entry.getValue() instanceof MultiTenantEventProcessor))
                               .map(Map.Entry::getKey)
                               .map(MultiTenantEventProcessorControlService::processorNameFromCombination)
                               .collect(Collectors.toList());
        return processorConfig.entrySet()
                              .stream()
                              .filter(entry -> {
                                  if (!processorNames.contains(entry.getKey())) {
                                      logger.info("Event Processor [{}] is not a registered. "
                                                          + "Please check the name or register the Event Processor",
                                                  entry.getKey());
                                      return false;
                                  }
                                  return true;
                              })
                              .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getLoadBalancingStrategy()));
    }

    private static String contextFromCombination(String processorAndContext) {
        return processorAndContext.substring(processorAndContext.indexOf("@") + 1);
    }

    private void registerInstructionHandler(ControlChannel controlChannel,
                                            String processorAndContext,
                                            EventProcessor processor) {
        controlChannel.registerEventProcessor(processorAndContext,
                                              infoSupplier(processor),
                                              new AxonProcessorInstructionHandler(processor, processorAndContext));
    }

    private void setLoadBalancingStrategy(AdminChannel adminChannel, String processorName, String strategy) {
        Optional<String> optionalIdentifier = tokenStoreIdentifierFor(processorName);
        if (!optionalIdentifier.isPresent()) {
            logger.warn("Cannot find token store identifier for processor [{}]. "
                                + "Load balancing cannot be configured without this identifier.", processorName);
            return;
        }
        String tokenStoreIdentifier = optionalIdentifier.get();

        adminChannel.loadBalanceEventProcessor(processorName, tokenStoreIdentifier, strategy)
                    .whenComplete((r, e) -> {
                        if (e == null) {
                            logger.debug("Successfully requested to load balance processor [{}]"
                                                 + " with strategy [{}].", processorName, strategy);
                            return;
                        }
                        logger.warn("Requesting to load balance processor [{}] with strategy [{}] failed.",
                                    processorName, strategy, e);
                    });
        if (processorConfig.get(processorName).isAutomaticBalancing()) {
            adminChannel.setAutoLoadBalanceStrategy(processorName, tokenStoreIdentifier, strategy)
                        .whenComplete((r, e) -> {
                            if (e == null) {
                                logger.debug("Successfully requested to automatically balance processor [{}]"
                                                     + " with strategy [{}].", processorName, strategy);
                                return;
                            }
                            logger.warn(
                                    "Requesting to automatically balance processor [{}] with strategy [{}] failed.",
                                    processorName, strategy, e
                            );
                        });
        }
    }

    private Optional<String> tokenStoreIdentifierFor(String processorName) {
        return eventProcessingConfiguration.tokenStore(processorName)
                                           .retrieveStorageIdentifier();
    }

    private static String processorNameFromCombination(String processorAndContext) {
        return processorAndContext.substring(0, processorAndContext.indexOf("@"));
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
