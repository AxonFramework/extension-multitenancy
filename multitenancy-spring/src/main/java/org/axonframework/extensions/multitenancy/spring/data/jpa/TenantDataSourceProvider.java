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

import org.axonframework.extensions.multitenancy.core.TenantDescriptor;

import javax.sql.DataSource;
import java.util.function.Function;

/**
 * Provider interface for obtaining tenant-specific {@link DataSource} instances.
 * <p>
 * Implementations of this interface are responsible for returning a {@link DataSource}
 * configured for a specific tenant. This is typically used in database-per-tenant
 * or schema-per-tenant multi-tenancy architectures.
 * <p>
 * Example implementations might:
 * <ul>
 *     <li>Return a pre-configured DataSource from a map of tenant databases</li>
 *     <li>Dynamically create DataSources using tenant-specific connection strings</li>
 *     <li>Return a routing DataSource that switches schemas based on tenant</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * TenantDataSourceProvider provider = tenant -> {
 *     String tenantId = tenant.tenantId();
 *     return DataSourceBuilder.create()
 *         .url("jdbc:postgresql://localhost:5432/" + tenantId)
 *         .username("app_user")
 *         .password("secret")
 *         .build();
 * };
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantEntityManagerFactoryBuilder
 */
@FunctionalInterface
public interface TenantDataSourceProvider extends Function<TenantDescriptor, DataSource> {

    /**
     * Returns a {@link DataSource} configured for the specified tenant.
     *
     * @param tenant the tenant descriptor identifying the tenant
     * @return a DataSource for the tenant's database
     * @throws IllegalArgumentException if the tenant is unknown or invalid
     */
    @Override
    DataSource apply(TenantDescriptor tenant);
}
