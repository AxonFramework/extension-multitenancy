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

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryUpdateEmitter;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQueryUpdateEmitterSegmentFactory;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for AxonServer, that configures abstract tenant-aware AxonServer components and factories.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
@AutoConfiguration
@ConditionalOnClass(AxonServerConfiguration.class)
@ConditionalOnProperty(value = {"axon.axonserver.enabled", "axon.multi-tenancy.enabled"}, matchIfMissing = true)
@AutoConfigureAfter(AxonServerAutoConfiguration.class)
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration.class")})
public class MultiTenancyAxonServerAutoConfiguration {

    @Autowired
    public void disableHeartBeat(AxonServerConfiguration axonServerConfiguration, Environment env) {
        if (!"true".equals(env.getProperty("axon.axonserver.heartbeat.enabled"))) {
            axonServerConfiguration.getHeartbeat().setEnabled(false);
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
            AxonServerConfiguration axonServerConfiguration,
            AxonServerConnectionManager connectionManager) {

        return tenantDescriptor -> AxonServerCommandBus.builder()
                                                       .localSegment(localSegment)
                                                       .serializer(messageSerializer)
                                                       .routingStrategy(routingStrategy)
                                                       .priorityCalculator(priorityCalculator)
                                                       .loadFactorProvider(loadFactorProvider)
                                                       .targetContextResolver(targetContextResolver)
                                                       .axonServerConnectionManager(connectionManager)
                                                       .configuration(axonServerConfiguration)
                                                       .defaultContext(tenantDescriptor.tenantId())
                                                       .build();
    }


    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.query.AxonServerQueryBus")
    public TenantQuerySegmentFactory tenantAxonServerQuerySegmentFactory(
            AxonServerConnectionManager axonServerConnectionManager,
            AxonServerConfiguration axonServerConfiguration,
            SpringAxonConfiguration axonConfiguration,
            TransactionManager txManager,
            @Qualifier("messageSerializer") Serializer messageSerializer,
            Serializer genericSerializer,
            QueryPriorityCalculator priorityCalculator,
            QueryInvocationErrorHandler queryInvocationErrorHandler,
            TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver,
            QueryUpdateEmitter multiTenantQueryUpdateEmitter) {
        return tenantDescriptor -> {
            SimpleQueryBus simpleQueryBus =
                    SimpleQueryBus.builder()
                                  .messageMonitor(axonConfiguration.getObject().messageMonitor(QueryBus.class,
                                                                                               "queryBus@"
                                                                                                       + tenantDescriptor))
                                  .transactionManager(txManager)
                                  .queryUpdateEmitter(multiTenantQueryUpdateEmitter)
                                  .errorHandler(queryInvocationErrorHandler)
                                  .build();
            simpleQueryBus.registerHandlerInterceptor(
                    new CorrelationDataInterceptor<>(axonConfiguration.getObject().correlationDataProviders())
            );

            return AxonServerQueryBus.builder()
                                     .axonServerConnectionManager(axonServerConnectionManager)
                                     .configuration(axonServerConfiguration)
                                     .localSegment(simpleQueryBus)
                                     .updateEmitter(((MultiTenantQueryUpdateEmitter) multiTenantQueryUpdateEmitter).getTenant(
                                             tenantDescriptor))
                                     .messageSerializer(messageSerializer)
                                     .genericSerializer(genericSerializer)
                                     .priorityCalculator(priorityCalculator)
                                     .targetContextResolver(targetContextResolver)
                                     .defaultContext(tenantDescriptor.tenantId())
                                     .build();
        };
    }

    @Bean
    @Primary
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.query.AxonServerQueryBus")
    public QueryUpdateEmitter multiTenantQueryUpdateEmitter(
            TenantQueryUpdateEmitterSegmentFactory tenantQueryUpdateEmitterSegmentFactory,
            TargetTenantResolver targetTenantResolver,
            TenantProvider tenantProvider) {

        MultiTenantQueryUpdateEmitter multiTenantQueryUpdateEmitter = MultiTenantQueryUpdateEmitter.builder()
                                                                                                   .tenantSegmentFactory(
                                                                                                           tenantQueryUpdateEmitterSegmentFactory)
                                                                                                   .targetTenantResolver(
                                                                                                           targetTenantResolver)
                                                                                                   .build();

        tenantProvider.subscribe(multiTenantQueryUpdateEmitter);

        return multiTenantQueryUpdateEmitter;
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.query.AxonServerQueryBus")
    public TenantQueryUpdateEmitterSegmentFactory tenantQueryUpdateEmitterSegmentFactory(
            SpringAxonConfiguration axonConfiguration) {
        return tenantDescriptor -> SimpleQueryUpdateEmitter.builder()
                                                           .updateMessageMonitor(axonConfiguration.getObject()
                                                                                                  .messageMonitor(
                                                                                                          QueryUpdateEmitter.class,
                                                                                                          "queryUpdateEmitter@"
                                                                                                                  + tenantDescriptor))
                                                           .build();
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantEventSegmentFactory tenantEventSegmentFactory(AxonServerConfiguration axonServerConfiguration,
                                                               SpringAxonConfiguration configuration,
                                                               AxonServerConnectionManager axonServerConnectionManager,
                                                               Serializer snapshotSerializer,
                                                               @Qualifier("eventSerializer") Serializer eventSerializer) {

        return tenant -> AxonServerEventStore.builder()
                                             .messageMonitor(configuration.getObject()
                                                                          .messageMonitor(AxonServerEventStore.class,
                                                                                          "eventStore@" + tenant))
                                             .configuration(axonServerConfiguration)
                                             .platformConnectionManager(axonServerConnectionManager)
                                             .snapshotSerializer(snapshotSerializer)
                                             .eventSerializer(eventSerializer)
                                             .defaultContext(tenant.tenantId())
                                             .snapshotFilter(configuration.getObject().snapshotFilter())
                                             .upcasterChain(configuration.getObject().upcasterChain())
                                             .build();
    }

    @Bean
    public EventProcessorInfoConfiguration processorInfoConfiguration(
            TenantProvider tenantProvider,
            AxonServerConnectionManager connectionManager) {
        return new EventProcessorInfoConfiguration(c -> {
            MultiTenantEventProcessorControlService controlService = new MultiTenantEventProcessorControlService(
                    connectionManager,
                    c.eventProcessingConfiguration(),
                    c.getComponent(AxonServerConfiguration.class));
            tenantProvider.subscribe(controlService);
            return controlService;
        });
    }
}