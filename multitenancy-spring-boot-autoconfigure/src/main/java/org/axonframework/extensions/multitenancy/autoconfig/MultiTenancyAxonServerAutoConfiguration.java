package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.extensions.multitenancy.commandbus.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.commandbus.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.commandbus.TenantProvider;
import org.axonframework.extensions.multitenancy.commandbus.TenantQuerySegmentFactory;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
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


    @Bean
    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
    public TenantProvider tenantProvider(@Value("${axon.axonserver.contexts:}") String contexts,
                                         TenantConnectPredicate tenantConnectPredicate) {
        return new AxonServerTenantProvider(contexts, tenantConnectPredicate);
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
    public TenantQuerySegmentFactory tenantAxonServerQuerySegmentFactory(AxonServerConnectionManager axonServerConnectionManager,
                                                                         AxonServerConfiguration axonServerConfiguration,
                                                                         AxonConfiguration axonConfiguration,
                                                                         TransactionManager txManager,
                                                                         @Qualifier("messageSerializer") Serializer messageSerializer,
                                                                         Serializer genericSerializer,
                                                                         QueryPriorityCalculator priorityCalculator,
                                                                         QueryInvocationErrorHandler queryInvocationErrorHandler,
                                                                         TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver) {
        return tenantDescriptor -> {
            SimpleQueryBus simpleQueryBus =
                    SimpleQueryBus.builder()
                            .messageMonitor(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"))
                            .transactionManager(txManager)
                            .queryUpdateEmitter(axonConfiguration.getComponent(QueryUpdateEmitter.class))
                            .errorHandler(queryInvocationErrorHandler)
                            .build();
            simpleQueryBus.registerHandlerInterceptor(
                    new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())
            );

            return AxonServerQueryBus.builder()
                    .axonServerConnectionManager(axonServerConnectionManager)
                    .configuration(axonServerConfiguration)
                    .localSegment(simpleQueryBus)
                    .updateEmitter(simpleQueryBus.queryUpdateEmitter())
                    .messageSerializer(messageSerializer)
                    .genericSerializer(genericSerializer)
                    .priorityCalculator(priorityCalculator)
                    .targetContextResolver(targetContextResolver)
                    .defaultContext(tenantDescriptor.tenantId())
                    .build();
        };
    }
}
