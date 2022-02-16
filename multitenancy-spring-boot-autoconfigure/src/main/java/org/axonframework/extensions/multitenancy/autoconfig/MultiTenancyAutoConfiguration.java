package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryBus;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.configuration.MultiTenantEventProcessingModule;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures Axon Server as implementation for multi-tenant components like CommandBus, QueryBus and EventStore.
 *
 * @author Stefan Dragisic
 */
@Configuration
@AutoConfigureAfter(MultiTenancyAxonServerAutoConfiguration.class)
public class MultiTenancyAutoConfiguration {

    private static final String DEFAULT_TENANT_CORRELATION_KEY = "tenantId";

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
                                                                           .tenantSegmentFactory(
                                                                                   tenantEventSegmentFactory)
                                                                           .targetTenantResolver(targetTenantResolver)
                                                                           .build();

        tenantProvider.subscribe(multiTenantEventStore);

        return multiTenantEventStore;
    }

    @Bean
    public MultiTenantEventProcessingModule multiTenantEventProcessingModule(TenantProvider tenantProvider) {
        return new MultiTenantEventProcessingModule(tenantProvider);
    }

    @Bean
    @ConditionalOnProperty(name = "axon.multitenant.use-metadata-helper", matchIfMissing = true)
    public TargetTenantResolver<Message<?>> targetTenantResolver() {
        return (message, tenants) ->
                TenantDescriptor.tenantWithId(
                        (String) message.getMetaData()
                                        .getOrDefault(DEFAULT_TENANT_CORRELATION_KEY, "unknownTenant")
                );
    }

    @Bean
    @ConditionalOnProperty(name = "axon.multitenant.use-metadata-helper", matchIfMissing = true)
    public CorrelationDataProvider tenantCorrelationProvider() {
        return new TenantCorrelationProvider(DEFAULT_TENANT_CORRELATION_KEY);
    }
}
