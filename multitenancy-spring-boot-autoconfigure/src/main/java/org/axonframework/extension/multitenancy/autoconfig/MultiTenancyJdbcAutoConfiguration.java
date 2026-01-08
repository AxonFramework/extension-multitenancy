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

import jakarta.annotation.Nonnull;
import org.axonframework.extension.multitenancy.core.TenantComponentRegistry;
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.extension.multitenancy.spring.data.jpa.TenantDataSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Auto-configuration for multi-tenant JDBC support.
 * <p>
 * This configuration is activated when {@code axon.multi-tenancy.jdbc.enabled=true}.
 * When enabled, it registers {@link JdbcTemplate} and {@link NamedParameterJdbcTemplate}
 * as tenant-scoped components that can be injected into message handlers.
 * <p>
 * This provides a lightweight alternative to JPA for projections and queries,
 * offering better performance for simple CRUD operations without ORM overhead.
 * <p>
 * Example usage:
 * <pre>{@code
 * @Component
 * public class CourseProjector {
 *     @EventHandler
 *     public void on(CourseCreatedEvent event, JdbcTemplate jdbcTemplate) {
 *         // jdbcTemplate is automatically scoped to the tenant from event metadata
 *         jdbcTemplate.update(
 *             "INSERT INTO courses (id, title) VALUES (?, ?)",
 *             event.courseId(), event.title()
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantDataSourceProvider
 * @see MultiTenancySpringDataJpaAutoConfiguration
 */
@AutoConfiguration(after = MultiTenancyAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean({TenantDataSourceProvider.class, TenantComponentResolverFactory.class, TenantProvider.class})
@ConditionalOnProperty(value = "axon.multi-tenancy.jdbc.enabled", havingValue = "true")
@EnableConfigurationProperties(MultiTenancyProperties.class)
public class MultiTenancyJdbcAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenancyJdbcAutoConfiguration.class);

    /**
     * Registers {@link JdbcTemplate} as a tenant-scoped component.
     * <p>
     * Each tenant will receive a {@link JdbcTemplate} instance configured with their
     * tenant-specific {@link javax.sql.DataSource}.
     *
     * @param dataSourceProvider the provider for tenant-specific DataSources
     * @param resolverFactory    the factory for creating tenant component resolvers
     * @param tenantProvider     the tenant provider for lifecycle management
     * @return a registry of tenant-scoped JdbcTemplate instances
     */
    @Bean
    @ConditionalOnMissingBean(name = "tenantJdbcTemplateRegistry")
    public TenantComponentRegistry<JdbcTemplate> tenantJdbcTemplateRegistry(
            @Nonnull TenantDataSourceProvider dataSourceProvider,
            @Nonnull TenantComponentResolverFactory resolverFactory,
            @Nonnull TenantProvider tenantProvider) {

        logger.debug("Registering JdbcTemplate as tenant component");

        TenantComponentRegistry<JdbcTemplate> registry = resolverFactory.registerComponent(
                JdbcTemplate.class,
                tenant -> {
                    logger.debug("Creating JdbcTemplate for tenant {}", tenant.tenantId());
                    return new JdbcTemplate(dataSourceProvider.apply(tenant));
                }
        );

        tenantProvider.subscribe(registry);
        tenantProvider.getTenants().forEach(registry::registerTenant);

        return registry;
    }

    /**
     * Registers {@link NamedParameterJdbcTemplate} as a tenant-scoped component.
     * <p>
     * Each tenant will receive a {@link NamedParameterJdbcTemplate} instance that
     * allows using named parameters (e.g., {@code :paramName}) instead of positional
     * parameters ({@code ?}) in SQL queries.
     *
     * @param dataSourceProvider the provider for tenant-specific DataSources
     * @param resolverFactory    the factory for creating tenant component resolvers
     * @param tenantProvider     the tenant provider for lifecycle management
     * @return a registry of tenant-scoped NamedParameterJdbcTemplate instances
     */
    @Bean
    @ConditionalOnMissingBean(name = "tenantNamedParameterJdbcTemplateRegistry")
    public TenantComponentRegistry<NamedParameterJdbcTemplate> tenantNamedParameterJdbcTemplateRegistry(
            @Nonnull TenantDataSourceProvider dataSourceProvider,
            @Nonnull TenantComponentResolverFactory resolverFactory,
            @Nonnull TenantProvider tenantProvider) {

        logger.debug("Registering NamedParameterJdbcTemplate as tenant component");

        TenantComponentRegistry<NamedParameterJdbcTemplate> registry = resolverFactory.registerComponent(
                NamedParameterJdbcTemplate.class,
                tenant -> {
                    logger.debug("Creating NamedParameterJdbcTemplate for tenant {}", tenant.tenantId());
                    return new NamedParameterJdbcTemplate(dataSourceProvider.apply(tenant));
                }
        );

        tenantProvider.subscribe(registry);
        tenantProvider.getTenants().forEach(registry::registerTenant);

        return registry;
    }
}
