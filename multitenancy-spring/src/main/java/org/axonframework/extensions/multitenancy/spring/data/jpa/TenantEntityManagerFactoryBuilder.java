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
package org.axonframework.extensions.multitenancy.spring.data.jpa;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Builder for creating tenant-specific {@link EntityManagerFactory} instances.
 * <p>
 * This class provides a fluent API for configuring how EntityManagerFactory instances
 * are created for each tenant. It caches created instances to ensure that the same
 * tenant always receives the same EntityManagerFactory.
 * <p>
 * The builder requires a {@link TenantDataSourceProvider} to obtain tenant-specific
 * DataSources, and optionally accepts configuration for:
 * <ul>
 *     <li>Packages to scan for JPA entities</li>
 *     <li>JPA properties (e.g., Hibernate settings)</li>
 *     <li>Persistence unit name</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * TenantEntityManagerFactoryBuilder builder = TenantEntityManagerFactoryBuilder
 *     .forDataSourceProvider(tenantDataSourceProvider)
 *     .packagesToScan("com.example.domain")
 *     .jpaProperty("hibernate.hbm2ddl.auto", "validate")
 *     .build();
 *
 * // Register with multi-tenancy configurer
 * multiTenancyConfigurer.tenantComponent(EntityManagerFactory.class, builder);
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantDataSourceProvider
 */
public class TenantEntityManagerFactoryBuilder implements Function<TenantDescriptor, EntityManagerFactory> {

    private static final Logger logger = LoggerFactory.getLogger(TenantEntityManagerFactoryBuilder.class);

    private final TenantDataSourceProvider dataSourceProvider;
    private final String[] packagesToScan;
    private final Map<String, Object> jpaProperties;
    private final String persistenceUnitName;
    private final Map<TenantDescriptor, EntityManagerFactory> emfCache = new ConcurrentHashMap<>();

    private TenantEntityManagerFactoryBuilder(Builder builder) {
        this.dataSourceProvider = builder.dataSourceProvider;
        this.packagesToScan = builder.packagesToScan;
        this.jpaProperties = new HashMap<>(builder.jpaProperties);
        this.persistenceUnitName = builder.persistenceUnitName;
    }

    /**
     * Creates a new builder with the specified {@link TenantDataSourceProvider}.
     *
     * @param dataSourceProvider the provider for tenant-specific DataSources
     * @return a new builder instance
     */
    public static Builder forDataSourceProvider(@Nonnull TenantDataSourceProvider dataSourceProvider) {
        return new Builder(dataSourceProvider);
    }

    /**
     * Returns the {@link EntityManagerFactory} for the specified tenant, creating it if necessary.
     * <p>
     * Created EntityManagerFactory instances are cached to ensure that the same tenant
     * always receives the same instance.
     *
     * @param tenant the tenant descriptor
     * @return the EntityManagerFactory for the tenant
     */
    @Override
    public EntityManagerFactory apply(TenantDescriptor tenant) {
        return emfCache.computeIfAbsent(tenant, this::createEntityManagerFactory);
    }

    /**
     * Returns the number of cached EntityManagerFactory instances.
     *
     * @return the cache size
     */
    public int cacheSize() {
        return emfCache.size();
    }

    private EntityManagerFactory createEntityManagerFactory(TenantDescriptor tenant) {
        DataSource dataSource = dataSourceProvider.apply(tenant);

        try {
            logger.debug("Creating EMF for tenant {} with datasource {}", tenant.tenantId(), dataSource.getConnection().getMetaData().getURL());
        } catch (Exception e) {
            logger.debug("Creating EMF for tenant {} with datasource {}", tenant.tenantId(), dataSource);
        }

        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPackagesToScan(packagesToScan);
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setPersistenceUnitName(persistenceUnitName + "-" + tenant.tenantId());
        factoryBean.setJpaPropertyMap(jpaProperties);
        factoryBean.afterPropertiesSet();

        return factoryBean.getObject();
    }

    /**
     * Builder for {@link TenantEntityManagerFactoryBuilder}.
     */
    public static class Builder {

        private final TenantDataSourceProvider dataSourceProvider;
        private String[] packagesToScan = new String[0];
        private final Map<String, Object> jpaProperties = new HashMap<>();
        private String persistenceUnitName = "tenant";

        private Builder(TenantDataSourceProvider dataSourceProvider) {
            this.dataSourceProvider = Objects.requireNonNull(dataSourceProvider,
                    "TenantDataSourceProvider may not be null");
        }

        /**
         * Sets the packages to scan for JPA entity classes.
         *
         * @param packages the packages to scan
         * @return this builder
         */
        public Builder packagesToScan(String... packages) {
            this.packagesToScan = packages;
            return this;
        }

        /**
         * Adds a JPA property to be used when creating EntityManagerFactory instances.
         *
         * @param key   the property key
         * @param value the property value
         * @return this builder
         */
        public Builder jpaProperty(String key, Object value) {
            this.jpaProperties.put(key, value);
            return this;
        }

        /**
         * Adds multiple JPA properties to be used when creating EntityManagerFactory instances.
         *
         * @param properties the properties to add
         * @return this builder
         */
        public Builder jpaProperties(Map<String, Object> properties) {
            this.jpaProperties.putAll(properties);
            return this;
        }

        /**
         * Sets the base persistence unit name. The tenant ID will be appended to this name.
         *
         * @param persistenceUnitName the base persistence unit name
         * @return this builder
         */
        public Builder persistenceUnitName(String persistenceUnitName) {
            this.persistenceUnitName = persistenceUnitName;
            return this;
        }

        /**
         * Builds the {@link TenantEntityManagerFactoryBuilder}.
         *
         * @return a new TenantEntityManagerFactoryBuilder instance
         */
        public TenantEntityManagerFactoryBuilder build() {
            return new TenantEntityManagerFactoryBuilder(this);
        }
    }
}
