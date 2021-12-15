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
import org.axonframework.config.EventProcessingConfiguration;
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
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;

/**
 * @author Stefan Dragisic
 */
@Configuration
@ConditionalOnClass(AxonServerConfiguration.class)
@ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
@AutoConfigureAfter(AxonServerAutoConfiguration.class)
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration.class")})
public class MultiTenancyAxonServerAutoConfiguration {

//    @Bean
//    public ConfigurerModule eventProcessorInfoConfiguration( AxonServerConnectionManager axonServerConnectionManager) {
//        return new ConfigurerModule() {
//            @Override
//            public int order() {
//                return 100;
//            }
//
//            @Override
//            public void configureModule(Configurer configurer) {
//                configurer.registerComponent(
//                        EventProcessorControlService.class,
//                        c -> new MultiTenantEventProcessorControlService(
//                                axonServerConnectionManager,
//                                c.eventProcessingConfiguration(),
//                                c.getComponent(AxonServerConfiguration.class)
//                        )
//                );
//            }
//        };
//    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantProvider tenantProvider(@Value("${axon.axonserver.contexts:}") String contexts,
                                         TenantConnectPredicate tenantConnectPredicate,
                                         AxonServerConnectionManager axonServerConnectionManager,
                                         AxonServerConfiguration axonServerConfiguration) {
        return new AxonServerTenantProvider(contexts,
                                            tenantConnectPredicate,
                                            axonServerConnectionManager,
                axonServerConfiguration);
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantCommandSegmentFactory tenantAxonServerCommandSegmentFactory(@Qualifier("messageSerializer") Serializer messageSerializer,
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
            AxonConfiguration axonConfiguration,
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
                                  .messageMonitor(axonConfiguration.messageMonitor(QueryBus.class,
                                                                                   "queryBus@" + tenantDescriptor))
                                  .transactionManager(txManager)
                                  .queryUpdateEmitter(multiTenantQueryUpdateEmitter)
                                  .errorHandler(queryInvocationErrorHandler)
                                  .build();
            simpleQueryBus.registerHandlerInterceptor(
                    new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())
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
            AxonConfiguration axonConfiguration) {
        return tenantDescriptor -> SimpleQueryUpdateEmitter.builder()
                                                           .updateMessageMonitor(axonConfiguration.messageMonitor(
                                                                   QueryUpdateEmitter.class,
                                                                   "queryUpdateEmitter@" + tenantDescriptor))
                                                           .build();
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantEventSegmentFactory tenantEventSegmentFactory(AxonServerConfiguration axonServerConfiguration,
                                                               AxonConfiguration configuration,
                                                               AxonServerConnectionManager axonServerConnectionManager,
                                                               Serializer snapshotSerializer,
                                                               @Qualifier("eventSerializer") Serializer eventSerializer) {

        return tenant -> AxonServerEventStore.builder()
                .messageMonitor(configuration
                                        .messageMonitor(AxonServerEventStore.class, "eventStore@" + tenant))
                .configuration(axonServerConfiguration)
                .platformConnectionManager(axonServerConnectionManager)
                .snapshotSerializer(snapshotSerializer)
                .eventSerializer(eventSerializer)
                .defaultContext(tenant.tenantId())
                .snapshotFilter(configuration.snapshotFilter())
                .upcasterChain(configuration.upcasterChain())
                .build();
    }

    @Bean
    public EventProcessorInfoConfiguration processorInfoConfiguration(
            EventProcessingConfiguration eventProcessingConfiguration,
            AxonServerConnectionManager connectionManager,
            AxonServerConfiguration configuration) {
        return new EventProcessorInfoConfiguration(c -> new MultiTenantEventProcessorControlService(
                connectionManager,
                c.eventProcessingConfiguration(),
                c.getComponent(AxonServerConfiguration.class)));
    }

//    @Bean
//    public EventProcessorControlService eventProcessorControlService(AxonServerConnectionManager connectionManager,
//                                                                     EventProcessingConfiguration eventProcessingConfiguration,
//                                                                     AxonServerConfiguration axonServerConfiguration) {
//        return new MultiTenantEventProcessorControlService(connectionManager,
//                                                           eventProcessingConfiguration,
//                                                           axonServerConfiguration);
//    }
}
