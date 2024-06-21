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
package org.axonframework.extensions.multitenancy.autoconfig;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.event.axon.*;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryUpdateEmitter;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQueryUpdateEmitterSegmentFactory;
import org.axonframework.extensions.multitenancy.components.scheduling.TenantEventSchedulerSegmentFactory;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.SpringAxonConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.PersistentStreamMessageSourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

/**
 * Autoconfiguration constructing the Axon Server specific tenant factories, with the {@link AxonServerTenantProvider}
 * at its core.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
@AutoConfiguration
@ConditionalOnClass(AxonServerConfiguration.class)
@ConditionalOnProperty(value = {"axon.axonserver.enabled", "axon.multi-tenancy.enabled"}, matchIfMissing = true)
@AutoConfigureAfter(AxonServerAutoConfiguration.class)
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration.class"
        )
})
public class MultiTenancyAxonServerAutoConfiguration {

    @Autowired
    public void disableHeartBeat(AxonServerConfiguration axonServerConfig, Environment env) {
        if (!"true".equals(env.getProperty("axon.axonserver.heartbeat.enabled"))) {
            axonServerConfig.getHeartbeat().setEnabled(false);
        }
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantProvider tenantProvider(Environment env,
                                         TenantConnectPredicate tenantConnectPredicate,
                                         AxonServerConnectionManager axonServerConnectionManager) {
        return new AxonServerTenantProvider(env.getProperty("axon.axonserver.contexts"),
                                            tenantConnectPredicate,
                                            axonServerConnectionManager);
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantCommandSegmentFactory tenantAxonServerCommandSegmentFactory(
            @Qualifier("messageSerializer") Serializer messageSerializer,
            @Qualifier("localSegment") CommandBus localSegment,
            RoutingStrategy routingStrategy,
            CommandPriorityCalculator priorityCalculator,
            CommandLoadFactorProvider loadFactorProvider,
            TargetContextResolver<? super CommandMessage<?>> targetContextResolver,
            AxonServerConfiguration axonServerConfig,
            AxonServerConnectionManager connectionManager
    ) {
        return tenantDescriptor -> {
            AxonServerCommandBus commandBus = AxonServerCommandBus.builder()
                                                             .localSegment(localSegment)
                                                             .serializer(messageSerializer)
                                                             .routingStrategy(routingStrategy)
                                                             .priorityCalculator(priorityCalculator)
                                                             .loadFactorProvider(loadFactorProvider)
                                                             .targetContextResolver(targetContextResolver)
                                                             .axonServerConnectionManager(connectionManager)
                                                             .configuration(axonServerConfig)
                                                             .defaultContext(tenantDescriptor.tenantId())
                                                             .build();
            commandBus.start();
            return commandBus;
        };
    }


    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.query.AxonServerQueryBus")
    public TenantQuerySegmentFactory tenantAxonServerQuerySegmentFactory(
            AxonServerConnectionManager axonServerConnectionManager,
            AxonServerConfiguration axonServerConfig,
            SpringAxonConfiguration axonConfig,
            TransactionManager txManager,
            @Qualifier("messageSerializer") Serializer messageSerializer,
            Serializer genericSerializer,
            QueryPriorityCalculator priorityCalculator,
            QueryInvocationErrorHandler queryInvocationErrorHandler,
            TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver,
            QueryUpdateEmitter multiTenantQueryUpdateEmitter
    ) {
        return tenantDescriptor -> {
            Configuration config = axonConfig.getObject();
            SimpleQueryBus simpleQueryBus =
                    SimpleQueryBus.builder()
                                  .messageMonitor(config.messageMonitor(
                                          QueryBus.class, "queryBus@" + tenantDescriptor
                                  ))
                                  .transactionManager(txManager)
                                  .queryUpdateEmitter(multiTenantQueryUpdateEmitter)
                                  .errorHandler(queryInvocationErrorHandler)
                                  .build();
            //noinspection resource
            simpleQueryBus.registerHandlerInterceptor(
                    new CorrelationDataInterceptor<>(config.correlationDataProviders())
            );

            AxonServerQueryBus queryBus = AxonServerQueryBus.builder()
                                                         .axonServerConnectionManager(axonServerConnectionManager)
                                                         .configuration(axonServerConfig)
                                                         .localSegment(simpleQueryBus)
                                                         .updateEmitter(
                                                                 ((MultiTenantQueryUpdateEmitter) multiTenantQueryUpdateEmitter)
                                                                         .getTenant(tenantDescriptor)
                                                         )
                                                         .messageSerializer(messageSerializer)
                                                         .genericSerializer(genericSerializer)
                                                         .priorityCalculator(priorityCalculator)
                                                         .targetContextResolver(targetContextResolver)
                                                         .defaultContext(tenantDescriptor.tenantId())
                                                         .build();

            queryBus.start();
            return queryBus;
        };
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.query.AxonServerQueryBus")
    public TenantQueryUpdateEmitterSegmentFactory tenantQueryUpdateEmitterSegmentFactory(
            SpringAxonConfiguration axonConfig
    ) {
        return tenantDescriptor -> {
            Configuration config = axonConfig.getObject();
            return SimpleQueryUpdateEmitter.builder()
                                           .updateMessageMonitor(config.messageMonitor(
                                                   QueryUpdateEmitter.class, "queryUpdateEmitter@" + tenantDescriptor
                                           ))
                                           .build();
        };
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantEventSegmentFactory tenantEventSegmentFactory(AxonServerConfiguration axonServerConfig,
                                                               SpringAxonConfiguration axonConfig,
                                                               AxonServerConnectionManager axonServerConnectionManager,
                                                               Serializer snapshotSerializer,
                                                               @Qualifier("eventSerializer") Serializer eventSerializer) {
        return tenant -> {
            Configuration config = axonConfig.getObject();
            return AxonServerEventStore.builder()
                                       .messageMonitor(config.messageMonitor(
                                               AxonServerEventStore.class, "eventStore@" + tenant
                                       ))
                                       .configuration(axonServerConfig)
                                       .platformConnectionManager(axonServerConnectionManager)
                                       .snapshotSerializer(snapshotSerializer)
                                       .eventSerializer(eventSerializer)
                                       .defaultContext(tenant.tenantId())
                                       .snapshotFilter(config.snapshotFilter())
                                       .upcasterChain(config.upcasterChain())
                                       .build();
        };
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.event.axon.AxonServerEventScheduler")
    public TenantEventSchedulerSegmentFactory tenantEventSchedulerSegmentFactory(
            AxonServerConnectionManager axonServerConnectionManager,
            Serializer serializer
    ) {
        return tenant -> {
            AxonServerEventScheduler eventScheduler = AxonServerEventScheduler.builder()
                                                                     .connectionManager(axonServerConnectionManager)
                                                                     .eventSerializer(serializer)
                                                                     .defaultContext(tenant.tenantId())
                                                                     .build();
            eventScheduler.start();
            return eventScheduler;
        };
    }

    @Bean
    public EventProcessorInfoConfiguration processorInfoConfiguration(
            TenantProvider tenantProvider,
            AxonServerConnectionManager connectionManager
    ) {
        return new EventProcessorInfoConfiguration(c -> {
            MultiTenantEventProcessorControlService controlService = new MultiTenantEventProcessorControlService(
                    connectionManager,
                    c.eventProcessingConfiguration(),
                    c.getComponent(AxonServerConfiguration.class)
            );
            tenantProvider.subscribe(controlService);
            return controlService;
        });
    }

    @Bean
    @Primary
    public ConfigurerModule persistentStreamProcessorsConfigurerModule(
            PersistentStreamMessageSourceFactory persistentStreamMessageSourceFactory,
            AxonServerConfiguration axonServerConfiguration) {
        return configurer -> configurer.eventProcessing(
                processingConfigurer ->
                        axonServerConfiguration.getEventhandling()
                                .getPersistentStreamProcessors()
                                .forEach((name, settings) -> {
                                    // for DLQ enabled processors
                                    processingConfigurer.registerSubscribingEventProcessor(
                                            name,
                                            configuration -> persistentStreamMessageSourceFactory.build(name,settings, configuration));
                                    if (settings.getDlq().isEnabled()) {
                                        processingConfigurer.registerSequencingPolicy(name,
                                                new PersistentStreamSequencingPolicy(
                                                        name,
                                                        settings));
                                        if (settings.getDlq().getCache().isEnabled()) {
                                            processingConfigurer.registerDeadLetteringEventHandlerInvokerConfiguration(
                                                    name,
                                                    (c, builder) -> builder
                                                            .enableSequenceIdentifierCache()
                                                            .sequenceIdentifierCacheSize(settings.getDlq()
                                                                    .getCache()
                                                                    .getSize()));
                                        }
                                    }
                                }));
    }

    @Bean
    @Primary
    public PersistentStreamMessageSourceFactory persistentStreamMessageSourceFactory(
            TenantProvider tenantProvider,
            @Qualifier("persistentStreamScheduler") ScheduledExecutorService scheduledExecutorService,
            TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory
            ) {
        return (name, settings, configuration) -> {
            MultiTenantPersistentStreamMessageSource component = new MultiTenantPersistentStreamMessageSource(configuration,
                    scheduledExecutorService,
                    name, settings,
                    tenantPersistentStreamMessageSourceFactory);
            tenantProvider.subscribe(component);
            return component;
        };
    }


    @Bean
    @ConditionalOnMissingBean
    public TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory(
            ScheduledExecutorService scheduledExecutorService
    ) {
        return (processorName, settings, tenantDescriptor, configuration) -> new PersistentStreamMessageSource(processorName + "@" + tenantDescriptor.tenantId(),
                configuration,
                new PersistentStreamProperties(processorName + "@" + tenantDescriptor.tenantId(),
                        settings.getInitialSegmentCount(),
                        settings.getSequencingPolicy(),
                        settings.getSequencingPolicyParameters(),
                        settings.getInitial(),
                        settings.getFilter()),
                scheduledExecutorService,
                settings.getBatchSize(),
                tenantDescriptor.tenantId());
    }
}