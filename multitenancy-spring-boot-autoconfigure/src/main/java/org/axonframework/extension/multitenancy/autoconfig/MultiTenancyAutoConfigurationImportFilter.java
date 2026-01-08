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
package org.axonframework.extension.multitenancy.autoconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Set;

/**
 * An {@link AutoConfigurationImportFilter} that excludes conflicting autoconfiguration
 * classes when multi-tenancy is enabled.
 * <p>
 * When {@code axon.multi-tenancy.enabled=true} (the default), this filter prevents
 * the following Axon Framework autoconfiguration classes from loading:
 * <ul>
 *     <li>{@code InfrastructureConfiguration} - Replaced by multi-tenant message handler lookup</li>
 *     <li>{@code JpaAutoConfiguration} - TokenStore replaced by TenantTokenStoreFactory</li>
 *     <li>{@code JpaEventStoreAutoConfiguration} - EventStore replaced by MultiTenantEventStore via SPI</li>
 * </ul>
 * <p>
 * Additionally, when {@code axon.multi-tenancy.jpa.tenant-repositories=true}, this filter
 * also excludes Spring Boot's JPA autoconfiguration to allow per-tenant EntityManagerFactory:
 * <ul>
 *     <li>{@code HibernateJpaAutoConfiguration} - Replaced by tenant-specific EMF instances</li>
 *     <li>{@code JpaRepositoriesAutoConfiguration} - Replaced by tenant-scoped repositories</li>
 * </ul>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see MultiTenantEventProcessingAutoConfiguration
 * @see MultiTenancySpringDataJpaAutoConfiguration
 */
public class MultiTenancyAutoConfigurationImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenancyAutoConfigurationImportFilter.class);

    /**
     * Axon Framework autoconfiguration classes to exclude when multi-tenancy is enabled.
     */
    private static final Set<String> EXCLUDED_WHEN_MULTITENANCY_ENABLED = Set.of(
            // Provides MessageHandlerLookup which creates standard (non-multi-tenant) event processors
            "org.axonframework.extension.springboot.autoconfig.InfrastructureConfiguration",
            // Provides single JpaTokenStore - we need TenantTokenStoreFactory for per-tenant stores
            "org.axonframework.extension.springboot.autoconfig.JpaAutoConfiguration",
            // Provides JPA EventStorageEngine - we use MultiTenantEventStore via SPI instead
            "org.axonframework.extension.springboot.autoconfig.JpaEventStoreAutoConfiguration"
    );

    /**
     * Spring Boot JPA autoconfiguration classes to exclude when tenant repositories are enabled.
     */
    private static final Set<String> EXCLUDED_WHEN_TENANT_REPOSITORIES_ENABLED = Set.of(
            // Provides default EntityManagerFactory - we use per-tenant EMF instances
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            // Provides default repository beans - we use tenant-scoped repositories
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
    );

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean multiTenancyEnabled = isMultiTenancyEnabled();
        boolean tenantRepositoriesEnabled = isTenantRepositoriesEnabled();

        if (autoConfigurationClasses.length > 1) {
            logger.debug("MultiTenancyAutoConfigurationImportFilter: multiTenancyEnabled={}, tenantRepositoriesEnabled={}",
                       multiTenancyEnabled, tenantRepositoriesEnabled);
        }

        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String className = autoConfigurationClasses[i];
            if (className == null) {
                matches[i] = true; // Include null entries (they're already filtered out elsewhere)
                continue;
            }

            // Exclude Axon Framework autoconfiguration when multi-tenancy is enabled
            if (multiTenancyEnabled && EXCLUDED_WHEN_MULTITENANCY_ENABLED.contains(className)) {
                logger.debug("Multi-tenancy enabled: EXCLUDING [{}]", className);
                matches[i] = false;
                continue;
            }

            // Exclude Spring JPA autoconfiguration when tenant repositories are enabled
            if (tenantRepositoriesEnabled && EXCLUDED_WHEN_TENANT_REPOSITORIES_ENABLED.contains(className)) {
                logger.debug("Tenant repositories enabled: EXCLUDING [{}]", className);
                matches[i] = false;
                continue;
            }

            matches[i] = true; // INCLUDE
        }
        return matches;
    }

    /**
     * Checks if multi-tenancy is enabled via the {@code axon.multi-tenancy.enabled} property.
     * Defaults to {@code true} if the property is not set.
     *
     * @return {@code true} if multi-tenancy is enabled, {@code false} otherwise
     */
    private boolean isMultiTenancyEnabled() {
        if (environment == null) {
            // Default to enabled if environment is not available
            return true;
        }
        return environment.getProperty("axon.multi-tenancy.enabled", Boolean.class, true);
    }

    /**
     * Checks if tenant repositories are enabled via the {@code axon.multi-tenancy.jpa.tenant-repositories} property.
     * Defaults to {@code false} if the property is not set.
     *
     * @return {@code true} if tenant repositories are enabled, {@code false} otherwise
     */
    private boolean isTenantRepositoriesEnabled() {
        if (environment == null) {
            return false;
        }
        return environment.getProperty("axon.multi-tenancy.jpa.tenant-repositories", Boolean.class, false);
    }
}
