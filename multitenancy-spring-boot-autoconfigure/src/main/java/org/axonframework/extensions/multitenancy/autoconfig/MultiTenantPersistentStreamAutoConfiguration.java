package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceFactory;
import org.axonframework.common.StringUtils;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

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
     * Creates a PersistentStreamMessageSourceFactory for multi-tenant environments.
     *
     * @param tenantProvider The TenantProvider for managing tenants.
     * @param tenantPersistentStreamMessageSourceFactory The factory for creating tenant-specific PersistentStreamMessageSources.
     * @return A PersistentStreamMessageSourceFactory that supports multi-tenancy.
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistentStreamMessageSourceFactory persistentStreamMessageSourceFactory(
            TenantProvider tenantProvider,
            TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory
    ) {
        return (name, persistentStreamProperties, scheduler, batchSize, context, configuration) -> {
            MultiTenantPersistentStreamMessageSource component = new MultiTenantPersistentStreamMessageSource(name, persistentStreamProperties, scheduler, batchSize, context, configuration,
                    tenantPersistentStreamMessageSourceFactory);
            tenantProvider.subscribe(component);
            return component;
        };
    }

    /**
     * Creates a TenantPersistentStreamMessageSourceFactory for creating tenant-specific PersistentStreamMessageSources.
     * @return A TenantPersistentStreamMessageSourceFactory.
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory(
    ) {
        return (   name,
                   persistentStreamProperties,
                   scheduler,
                   batchSize,
                   context,
                   configuration,
                   tenantDescriptor) ->
                new PersistentStreamMessageSource(name + "@" + tenantDescriptor.tenantId(),
                        configuration,
                        persistentStreamProperties,
                        scheduler,
                        batchSize,
                        StringUtils.emptyOrNull(context) ? tenantDescriptor.tenantId() : context);
    }
}