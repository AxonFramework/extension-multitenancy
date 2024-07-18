/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.extensions.multitenancy.components.TenantEventProcessorControlSegmentFactory;
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
    private final TenantEventProcessorControlSegmentFactory tenantEventProcessorControlSegmentFactory;

    /**
     * Initialize a {@link MultiTenantEventProcessorControlService}.
     * <p>
     * This service adds processor instruction handlers to the {@link ControlChannel} of the given {@code context}, for
     * every tenant. Doing so ensures operation like the {@link EventProcessor#start() start} and
     * {@link EventProcessor#shutDown() shutdown} can be triggered through Axon Server. Furthermore, it sets the
     * configured load balancing strategies through the {@link AdminChannel}  of the {@code context}.
     *
     * @param axonServerConnectionManager               A {@link AxonServerConnectionManager} from which to retrieve the
     *                                                  {@link ControlChannel} and {@link AdminChannel}.
     * @param eventProcessingConfiguration              The {@link EventProcessor} configuration of this application,
     *                                                  used to retrieve the registered event processors from.
     * @param axonServerConfiguration                   The {@link AxonServerConfiguration} used to retrieve the
     *                                                  {@link AxonServerConnectionManager#getDefaultContext() default
     *                                                  context} from.
     * @param tenantEventProcessorControlSegmentFactory The {@link TenantEventProcessorControlSegmentFactory} used to
     *                                                  retrieve the context name for the given tenant.
     */
    public MultiTenantEventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                                   EventProcessingConfiguration eventProcessingConfiguration,
                                                   AxonServerConfiguration axonServerConfiguration,
                                                   TenantEventProcessorControlSegmentFactory tenantEventProcessorControlSegmentFactory) {
        super(axonServerConnectionManager,
             eventProcessingConfiguration,
             axonServerConfiguration.getContext(),
             axonServerConfiguration.getEventhandling().getProcessors());
        this.tenantEventProcessorControlSegmentFactory = tenantEventProcessorControlSegmentFactory;
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
                               .map(this::processorNameFromCombination)
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

    private String processorNameFromCombination(String processorAndTenantId) {
        int index = processorAndTenantId.indexOf("@");
        return index == -1 ? processorAndTenantId : processorAndTenantId.substring(0, index);
    }

    private String contextFromCombination(String processorAndTenantId) {
        int index = processorAndTenantId.indexOf("@");
        //if there is no context name in the processorAndContext, return the _admin as default
        String tenantId = index == -1 ? "_admin" : processorAndTenantId.substring(index + 1);
        if ("_admin".equals(tenantId)) {
            return "_admin";
        }
        return tenantEventProcessorControlSegmentFactory.apply(TenantDescriptor.tenantWithId(tenantId));
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
            String context = tenantEventProcessorControlSegmentFactory.apply(tenantDescriptor);
            ControlChannel controlChannel = axonServerConnectionManager.getConnection(context)
                                                                       .controlChannel();
            AxonProcessorInstructionHandler instructionHandler = new AxonProcessorInstructionHandler(processor, name);
            controlChannel.registerEventProcessor(name, infoSupplier(processor), instructionHandler);
        });
        return () -> true;
    }
}
