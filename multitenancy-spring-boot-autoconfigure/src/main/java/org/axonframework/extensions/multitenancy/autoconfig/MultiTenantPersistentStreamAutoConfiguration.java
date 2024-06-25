package org.axonframework.extensions.multitenancy.autoconfig;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.PersistentStreamMessageSourceFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@AutoConfiguration
@ConditionalOnProperty(value = {"axon.axonserver.enabled", "axon.multi-tenancy.enabled"}, matchIfMissing = true)
@AutoConfigureBefore(AxonServerAutoConfiguration.class)
public class MultiTenantPersistentStreamAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public ScheduledExecutorService multiTenantpersistentStreamScheduler(AxonServerConfiguration axonServerConfiguration) {
        return Executors.newScheduledThreadPool(axonServerConfiguration.getPersistentStreamThreads(),
                new AxonThreadFactory("persistent-streams"));
    }

    @Bean
    @Primary
    public PersistentStreamMessageSourceFactory persistentStreamMessageSourceFactory(
            TenantProvider tenantProvider,
            @Qualifier("multiTenantpersistentStreamScheduler") ScheduledExecutorService scheduledExecutorService,
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
