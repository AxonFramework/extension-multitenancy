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
import org.axonframework.extension.multitenancy.spring.data.r2dbc.TenantConnectionFactoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Auto-configuration for multi-tenant R2DBC (reactive database) support.
 * <p>
 * This configuration is activated when {@code axon.multi-tenancy.r2dbc.enabled=true}.
 * When enabled, it registers {@link DatabaseClient} as a tenant-scoped component
 * that can be injected into message handlers for non-blocking database operations.
 * <p>
 * R2DBC provides reactive, non-blocking database access which can be beneficial
 * in high-concurrency scenarios when not using virtual threads.
 * <p>
 * Example usage:
 * <pre>{@code
 * @Component
 * public class CourseProjector {
 *     @EventHandler
 *     public Mono<Void> on(CourseCreatedEvent event, DatabaseClient databaseClient) {
 *         // databaseClient is automatically scoped to the tenant from event metadata
 *         return databaseClient
 *             .sql("INSERT INTO courses (id, title) VALUES (:id, :title)")
 *             .bind("id", event.courseId())
 *             .bind("title", event.title())
 *             .then();
 *     }
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantConnectionFactoryProvider
 * @see MultiTenancyJdbcAutoConfiguration
 */
@AutoConfiguration(after = MultiTenancyAutoConfiguration.class)
@ConditionalOnClass(DatabaseClient.class)
@ConditionalOnBean({TenantConnectionFactoryProvider.class, TenantComponentResolverFactory.class, TenantProvider.class})
@ConditionalOnProperty(value = "axon.multi-tenancy.r2dbc.enabled", havingValue = "true")
@EnableConfigurationProperties(MultiTenancyProperties.class)
public class MultiTenancyR2dbcAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenancyR2dbcAutoConfiguration.class);

    /**
     * Registers {@link DatabaseClient} as a tenant-scoped component.
     * <p>
     * Each tenant will receive a {@link DatabaseClient} instance configured with their
     * tenant-specific {@link io.r2dbc.spi.ConnectionFactory}.
     *
     * @param connectionFactoryProvider the provider for tenant-specific ConnectionFactories
     * @param resolverFactory           the factory for creating tenant component resolvers
     * @param tenantProvider            the tenant provider for lifecycle management
     * @return a registry of tenant-scoped DatabaseClient instances
     */
    @Bean
    @ConditionalOnMissingBean(name = "tenantDatabaseClientRegistry")
    public TenantComponentRegistry<DatabaseClient> tenantDatabaseClientRegistry(
            @Nonnull TenantConnectionFactoryProvider connectionFactoryProvider,
            @Nonnull TenantComponentResolverFactory resolverFactory,
            @Nonnull TenantProvider tenantProvider) {

        logger.debug("Registering DatabaseClient as tenant component");

        TenantComponentRegistry<DatabaseClient> registry = resolverFactory.registerComponent(
                DatabaseClient.class,
                tenant -> {
                    logger.debug("Creating DatabaseClient for tenant {}", tenant.tenantId());
                    return DatabaseClient.create(connectionFactoryProvider.apply(tenant));
                }
        );

        tenantProvider.subscribe(registry);
        tenantProvider.getTenants().forEach(registry::registerTenant);

        return registry;
    }
}
