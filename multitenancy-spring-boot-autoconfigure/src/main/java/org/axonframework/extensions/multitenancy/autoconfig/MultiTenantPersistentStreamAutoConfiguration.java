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

/**
 * Auto-configuration class for multi-tenant persistent stream support in Axon Framework.
 * This configuration is enabled when Axon Server and multi-tenancy are both enabled.
 *
 * @author Stefan Dragisic
 * @since 4.10
 */
@AutoConfiguration
@ConditionalOnProperty(value = {"axon.axonserver.enabled", "axon.multi-tenancy.enabled"}, matchIfMissing = true)
@AutoConfigureBefore(AxonServerAutoConfiguration.class)
public class MultiTenantPersistentStreamAutoConfiguration {

    /**
     * Creates a ScheduledExecutorService for multi-tenant persistent streams.
     *
     * @param axonServerConfiguration The Axon Server configuration.
     * @return A ScheduledExecutorService for persistent streams.
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduledExecutorService multiTenantPersistentStreamScheduler(AxonServerConfiguration axonServerConfiguration) {
        return Executors.newScheduledThreadPool(axonServerConfiguration.getPersistentStreamThreads(),
                new AxonThreadFactory("persistent-streams"));
    }

    /**
     * Creates a PersistentStreamMessageSourceFactory for multi-tenant environments.
     *
     * @param tenantProvider The TenantProvider for managing tenants.
     * @param scheduledExecutorService The ScheduledExecutorService for persistent streams.
     * @param tenantPersistentStreamMessageSourceFactory The factory for creating tenant-specific PersistentStreamMessageSources.
     * @return A PersistentStreamMessageSourceFactory that supports multi-tenancy.
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistentStreamMessageSourceFactory persistentStreamMessageSourceFactory(
            TenantProvider tenantProvider,
            @Qualifier("multiTenantPersistentStreamScheduler") ScheduledExecutorService scheduledExecutorService,
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

    /**
     * Creates a TenantPersistentStreamMessageSourceFactory for creating tenant-specific PersistentStreamMessageSources.
     *
     * @param scheduledExecutorService The ScheduledExecutorService for persistent streams.
     * @return A TenantPersistentStreamMessageSourceFactory.
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory(
            ScheduledExecutorService scheduledExecutorService
    ) {
        return (processorName, settings, tenantDescriptor, configuration) -> new PersistentStreamMessageSource(processorName + "@" + tenantDescriptor.tenantId(),
                configuration,
                new PersistentStreamProperties(processorName,
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