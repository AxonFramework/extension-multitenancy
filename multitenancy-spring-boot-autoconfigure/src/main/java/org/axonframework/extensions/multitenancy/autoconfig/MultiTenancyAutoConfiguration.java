package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.extensions.multitenancy.commandbus.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantConnectorConfigurerModule;
import org.axonframework.extensions.multitenancy.commandbus.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.commandbus.TenantDescriptor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Stefan Dragisic
 */
@Configuration
@ConditionalOnClass(MultiTenantConnectorConfigurerModule.class)
@AutoConfigureAfter(name = {
        "org.axonframework.springboot.autoconfig.InfraConfiguration",
        "org.axonframework.springboot.autoconfig.AxonAutoConfiguration",
        "org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration"
})
public class MultiTenancyAutoConfiguration {

    @Bean
    public MultiTenantCommandBus multiTenantCommandBus(TargetTenantResolver targetTenantResolver) {
        return MultiTenantCommandBus.builder()
                .targetTenantResolver(targetTenantResolver)
                .build();
    }

    @Bean
    public TargetTenantResolver targetTenantResolver() {
        return (message, tenantDescriptors) -> TenantDescriptor.tenantWithId("default");
    }

    //todo same for event and query bus

//    @Bean
//    @ConditionalOnClass(name = "org.axonframework.axonserver.connector.command.AxonServerCommandBus")
//    public TenantCommandSegmentFactory tenantAxonServerCommandSegmentFactory(@Qualifier("messageSerializer") Serializer messageSerializer,
//                                                                             RoutingStrategy routingStrategy,
//                                                                             CommandPriorityCalculator priorityCalculator,
//                                                                             CommandLoadFactorProvider loadFactorProvider,
//                                                                             TargetContextResolver<? super CommandMessage<?>> targetContextResolver) {
//        return tenantDescriptor -> {
//            AxonServerCommandBus.Builder builder = AxonServerCommandBus.builder()
//                    .localSegment(SimpleCommandBus.builder().build())
//                    .serializer(messageSerializer)
//                    .routingStrategy(routingStrategy)
//                    .priorityCalculator(priorityCalculator)
//                    .loadFactorProvider(loadFactorProvider)
//                    .targetContextResolver(targetContextResolver);
//
//            AxonServerConfiguration customContextConfig = AxonServerConfiguration.builder()
//                    .servers("localhost")
//                    .context(tenantDescriptor.tenantId())
//                    .componentName("demo-app")
//                    .build();
//
//            return builder.configuration(customContextConfig)
//                    .axonServerConnectionManager(AxonServerConnectionManager.builder()
//                            .axonServerConfiguration(customContextConfig)
//                            .build())
//                    .build();
//
//        };
//    }

}
