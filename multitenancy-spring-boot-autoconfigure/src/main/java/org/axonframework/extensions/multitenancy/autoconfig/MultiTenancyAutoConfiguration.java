package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantConnectorConfigurerModule;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantQueryBus;
import org.axonframework.extensions.multitenancy.commandbus.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.commandbus.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.commandbus.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.commandbus.TenantDescriptor;
import org.axonframework.extensions.multitenancy.commandbus.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.commandbus.TenantProvider;
import org.axonframework.extensions.multitenancy.commandbus.TenantQuerySegmentFactory;
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

        tenantProvider.subscribe(commandBus);

        return commandBus;
    }

    @Bean
    @Primary
    public MultiTenantQueryBus multiTenantQueryBus(TenantQuerySegmentFactory tenantQuerySegmentFactory,
                                                   TargetTenantResolver targetTenantResolver,
                                                   TenantProvider tenantProvider) {

        MultiTenantQueryBus queryBus = MultiTenantQueryBus.builder()
                .tenantSegmentFactory(tenantQuerySegmentFactory)
                .targetTenantResolver(targetTenantResolver)
                .build();

        tenantProvider.subscribe(queryBus);

        return queryBus;
    }

    @Bean
    @Primary
    public MultiTenantEventStore multiTenantEventStore(TenantEventSegmentFactory tenantEventSegmentFactory,
                                                       TargetTenantResolver targetTenantResolver,
                                                       TenantProvider tenantProvider) {

        MultiTenantEventStore multiTenantEventStore = MultiTenantEventStore.builder()
                .tenantSegmentFactory(tenantEventSegmentFactory)
                .targetTenantResolver(targetTenantResolver)
                .build();

        tenantProvider.subscribe(multiTenantEventStore);

        return multiTenantEventStore;
    }

}
