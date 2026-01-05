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
package org.axonframework.extensions.multitenancy.messaging.eventhandling.processing;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;

import java.util.function.Function;

/**
 * Factory for creating tenant-specific {@link ConnectionProvider} instances.
 * <p>
 * This factory is used by {@link JdbcTenantTokenStoreFactory} to create
 * per-tenant JDBC connections for token storage.
 * <p>
 * Implementations should ensure that each tenant gets connections to the appropriate
 * database. For example, when using a database-per-tenant architecture, each tenant's
 * {@link ConnectionProvider} should return connections to that tenant's specific database.
 * <p>
 * Example implementation using DataSource per tenant:
 * <pre>{@code
 * TenantConnectionProviderFactory factory = tenant -> {
 *     DataSource tenantDataSource = getDataSourceForTenant(tenant);
 *     return tenantDataSource::getConnection;
 * };
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see ConnectionProvider
 * @see JdbcTenantTokenStoreFactory
 */
@FunctionalInterface
public interface TenantConnectionProviderFactory extends Function<TenantDescriptor, ConnectionProvider> {

    /**
     * Creates or retrieves a {@link ConnectionProvider} for the specified tenant.
     *
     * @param tenant The tenant descriptor identifying the tenant.
     * @return A {@link ConnectionProvider} that provides connections to the tenant's database.
     */
    @Override
    ConnectionProvider apply(TenantDescriptor tenant);
}
