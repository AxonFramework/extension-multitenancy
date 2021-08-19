package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantConnectorConfigurerModule;
import org.axonframework.extensions.multitenancy.commandbus.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.commandbus.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.commandbus.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.commandbus.TenantDescriptor;
import org.axonframework.extensions.multitenancy.commandbus.TenantProvider;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author Stefan Dragisic
 */
@Configuration
@ConditionalOnClass(MultiTenantConnectorConfigurerModule.class)
@AutoConfigureAfter(MultiTenancyAxonServerAutoConfiguration.class)

public class MultiTenancyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TargetTenantResolver targetTenantResolver() {
        return (message, tenantDescriptors) -> TenantDescriptor.tenantWithId("default");
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantConnectPredicate tenantFilterPredicate() {
        return tenant -> true;
    }

    @Bean
    @Primary
    @ConditionalOnMissingQualifiedBean(qualifier = "!localSegment", beanClass = CommandBus.class)
    public MultiTenantCommandBus multiTenantCommandBus(TenantCommandSegmentFactory tenantCommandSegmentFactory,
                                                       TargetTenantResolver targetTenantResolver,
                                                       TenantProvider tenantProvider) {

        MultiTenantCommandBus commandBus = MultiTenantCommandBus.builder()
                .tenantSegmentFactory(tenantCommandSegmentFactory)
                .targetTenantResolver(targetTenantResolver)
                .build();

        tenantProvider.get()
                .forEach(commandBus::registerTenant);

        tenantProvider.subscribeTenantUpdates(commandBus);

        return commandBus;
    }



}
