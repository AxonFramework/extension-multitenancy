/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.extension.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.extensions.multitenancy.axonserver.AxonServerTenantProvider;
import org.axonframework.extensions.multitenancy.core.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.core.TenantProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Axon Server multi-tenancy integration.
 * <p>
 * This configuration provides property binding for the {@code AxonServerTenantProvider}
 * which is auto-registered via SPI by the {@code multitenancy-axon-server-connector} module.
 * <p>
 * When Axon Server is available and multi-tenancy is enabled, this configuration ensures
 * that the {@link AxonServerTenantProvider} is properly configured with:
 * <ul>
 *     <li>Predefined contexts from {@code axon.multi-tenancy.axon-server.contexts}</li>
 *     <li>Admin context filtering from {@code axon.multi-tenancy.axon-server.filter-admin-contexts}</li>
 * </ul>
 * <p>
 * The actual {@link TenantProvider} registration is handled by
 * {@code DistributedMultiTenancyConfigurationDefaults} in the connector module via SPI. This auto-configuration
 * only provides property binding and conditional bean overrides.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see AxonServerTenantProvider
 * @see MultiTenancyProperties
 */
@AutoConfiguration
@AutoConfigureAfter(AxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
@ConditionalOnProperty(value = {"axon.axonserver.enabled", "axon.multi-tenancy.enabled"}, matchIfMissing = true)
@EnableConfigurationProperties(MultiTenancyProperties.class)
public class MultiTenancyAxonServerAutoConfiguration {

    /**
     * Creates an {@link AxonServerTenantProvider} with property-based configuration.
     * <p>
     * This bean is only created if:
     * <ul>
     *     <li>An {@link AxonServerConnectionManager} is available</li>
     *     <li>No other {@link TenantProvider} has been registered</li>
     * </ul>
     * <p>
     * In most cases, the {@code DistributedMultiTenancyConfigurationDefaults} from the connector module
     * will have already registered the provider via SPI. This bean serves as a fallback
     * that includes Spring Boot property binding for contexts and filtering.
     *
     * @param properties              the multi-tenancy configuration properties
     * @param tenantConnectPredicate  predicate for filtering which contexts become tenants
     * @param connectionManager       the Axon Server connection manager
     * @return the configured Axon Server tenant provider
     */
    @Bean
    @ConditionalOnBean(AxonServerConnectionManager.class)
    @ConditionalOnMissingBean(TenantProvider.class)
    public TenantProvider tenantProvider(MultiTenancyProperties properties,
                                         TenantConnectPredicate tenantConnectPredicate,
                                         AxonServerConnectionManager connectionManager) {
        MultiTenancyProperties.AxonServerProperties axonServerProps = properties.getAxonServer();

        TenantConnectPredicate effectivePredicate = tenantConnectPredicate;
        if (axonServerProps.isFilterAdminContexts()) {
            // Filter out admin contexts (those starting with "_")
            effectivePredicate = tenant ->
                    tenantConnectPredicate.test(tenant) &&
                    !tenant.tenantId().startsWith("_");
        }

        return AxonServerTenantProvider.builder()
                                       .axonServerConnectionManager(connectionManager)
                                       .preDefinedContexts(axonServerProps.getContexts())
                                       .tenantConnectPredicate(effectivePredicate)
                                       .build();
    }
}
