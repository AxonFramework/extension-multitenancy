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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Axon Framework multi-tenancy extension.
 * <p>
 * These properties allow customization of multi-tenancy behavior including
 * enabling/disabling the feature, configuring the tenant key used in message
 * metadata, and providing a static list of tenants for non-Axon Server deployments.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
@ConfigurationProperties("axon.multi-tenancy")
public class MultiTenancyProperties {

    /**
     * Whether multi-tenancy is enabled. Defaults to {@code true}.
     */
    private boolean enabled = true;

    /**
     * The metadata key used to identify the tenant. Defaults to {@code "tenantId"}.
     * This key is used by {@link org.axonframework.extensions.multitenancy.core.MetadataBasedTenantResolver}
     * to extract the tenant from message metadata.
     */
    private String tenantKey = TenantConfiguration.TENANT_CORRELATION_KEY;

    /**
     * Static list of tenant identifiers. Use this for non-Axon Server deployments
     * where tenants are known at configuration time.
     * <p>
     * When using Axon Server, tenants are typically discovered dynamically from
     * Axon Server contexts via the {@code multitenancy-axon-server-connector} module.
     */
    private List<String> tenants = new ArrayList<>();

    /**
     * Axon Server specific configuration for multi-tenancy.
     */
    private AxonServerProperties axonServer = new AxonServerProperties();

    /**
     * Data access configuration for multi-tenant repositories.
     */
    private DataAccessProperties dataAccess = new DataAccessProperties();

    /**
     * JPA-specific configuration for multi-tenant data access.
     */
    private JpaProperties jpa = new JpaProperties();

    /**
     * Returns whether multi-tenancy is enabled.
     *
     * @return {@code true} if multi-tenancy is enabled, {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether multi-tenancy is enabled.
     *
     * @param enabled {@code true} to enable multi-tenancy, {@code false} to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the metadata key used to identify the tenant.
     *
     * @return the tenant key
     */
    public String getTenantKey() {
        return tenantKey;
    }

    /**
     * Sets the metadata key used to identify the tenant.
     *
     * @param tenantKey the tenant key
     */
    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    /**
     * Returns the static list of tenant identifiers.
     *
     * @return the list of tenant identifiers
     */
    public List<String> getTenants() {
        return tenants;
    }

    /**
     * Sets the static list of tenant identifiers.
     *
     * @param tenants the list of tenant identifiers
     */
    public void setTenants(List<String> tenants) {
        this.tenants = tenants;
    }

    /**
     * Returns the Axon Server specific configuration.
     *
     * @return the Axon Server properties
     */
    public AxonServerProperties getAxonServer() {
        return axonServer;
    }

    /**
     * Sets the Axon Server specific configuration.
     *
     * @param axonServer the Axon Server properties
     */
    public void setAxonServer(AxonServerProperties axonServer) {
        this.axonServer = axonServer;
    }

    /**
     * Returns the data access configuration.
     *
     * @return the data access properties
     */
    public DataAccessProperties getDataAccess() {
        return dataAccess;
    }

    /**
     * Sets the data access configuration.
     *
     * @param dataAccess the data access properties
     */
    public void setDataAccess(DataAccessProperties dataAccess) {
        this.dataAccess = dataAccess;
    }

    /**
     * Returns the JPA-specific configuration.
     *
     * @return the JPA properties
     */
    public JpaProperties getJpa() {
        return jpa;
    }

    /**
     * Sets the JPA-specific configuration.
     *
     * @param jpa the JPA properties
     */
    public void setJpa(JpaProperties jpa) {
        this.jpa = jpa;
    }

    /**
     * Axon Server specific properties for multi-tenancy configuration.
     * <p>
     * These properties are passed to the {@code AxonServerTenantProvider} in the
     * {@code multitenancy-axon-server-connector} module when Axon Server is used.
     */
    public static class AxonServerProperties {

        /**
         * Comma-separated list of predefined Axon Server context names to use as tenants.
         * When set, the tenant provider will not query Axon Server's Admin API for contexts
         * and will not subscribe to context update events.
         * <p>
         * Leave empty to discover tenants dynamically from Axon Server.
         */
        private String contexts;

        /**
         * Whether to filter out admin contexts (those starting with "_") from
         * the list of tenants. Defaults to {@code true}.
         */
        private boolean filterAdminContexts = true;

        /**
         * Returns the comma-separated list of predefined context names.
         *
         * @return the predefined contexts, or {@code null} if dynamic discovery is used
         */
        public String getContexts() {
            return contexts;
        }

        /**
         * Sets the comma-separated list of predefined context names.
         *
         * @param contexts the predefined contexts
         */
        public void setContexts(String contexts) {
            this.contexts = contexts;
        }

        /**
         * Returns whether admin contexts should be filtered out.
         *
         * @return {@code true} if admin contexts are filtered, {@code false} otherwise
         */
        public boolean isFilterAdminContexts() {
            return filterAdminContexts;
        }

        /**
         * Sets whether admin contexts should be filtered out.
         *
         * @param filterAdminContexts {@code true} to filter admin contexts
         */
        public void setFilterAdminContexts(boolean filterAdminContexts) {
            this.filterAdminContexts = filterAdminContexts;
        }
    }

    /**
     * Data access properties for multi-tenant Spring Data repository injection.
     * <p>
     * When enabled, Spring Data JPA repositories can be injected as handler parameters
     * and will automatically be scoped to the current message's tenant.
     */
    public static class DataAccessProperties {

        /**
         * Whether tenant-scoped data access is enabled. Defaults to {@code true}.
         * <p>
         * When enabled, event handlers can receive tenant-scoped Spring Data repositories
         * as parameters, automatically configured for the message's tenant.
         */
        private boolean enabled = true;

        /**
         * Returns whether tenant-scoped data access is enabled.
         *
         * @return {@code true} if enabled, {@code false} otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether tenant-scoped data access is enabled.
         *
         * @param enabled {@code true} to enable tenant-scoped data access
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * JPA-specific properties for multi-tenant data access.
     * <p>
     * When {@code tenant-repositories} is enabled, Spring Boot's default JPA autoconfiguration
     * is excluded (HibernateJpaAutoConfiguration, JpaRepositoriesAutoConfiguration), and
     * tenant-specific EntityManagerFactory instances are used instead.
     */
    public static class JpaProperties {

        /**
         * Whether to enable per-tenant JPA repositories. Defaults to {@code false}.
         * <p>
         * When enabled:
         * <ul>
         *     <li>Spring Boot's default JPA autoconfiguration is excluded</li>
         *     <li>A {@link org.axonframework.extensions.multitenancy.spring.data.jpa.TenantDataSourceProvider}
         *         bean is required</li>
         *     <li>Spring Data repositories extending {@link org.springframework.data.repository.Repository}
         *         are automatically scoped to the current message's tenant</li>
         * </ul>
         * <p>
         * When disabled, JPA works normally with Spring Boot's default single-datasource configuration.
         */
        private boolean tenantRepositories = false;

        /**
         * Returns whether per-tenant JPA repositories are enabled.
         *
         * @return {@code true} if tenant repositories are enabled, {@code false} otherwise
         */
        public boolean isTenantRepositories() {
            return tenantRepositories;
        }

        /**
         * Sets whether per-tenant JPA repositories are enabled.
         *
         * @param tenantRepositories {@code true} to enable per-tenant repositories
         */
        public void setTenantRepositories(boolean tenantRepositories) {
            this.tenantRepositories = tenantRepositories;
        }
    }
}
