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
package org.axonframework.extension.multitenancy.spring.data.r2dbc;

import io.r2dbc.spi.ConnectionFactory;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;

import java.util.function.Function;

/**
 * Provider interface for obtaining tenant-specific R2DBC {@link ConnectionFactory} instances.
 * <p>
 * Implementations of this interface are responsible for returning a {@link ConnectionFactory}
 * configured for a specific tenant. This is used in reactive database-per-tenant
 * or schema-per-tenant multi-tenancy architectures.
 * <p>
 * Example usage:
 * <pre>{@code
 * TenantConnectionFactoryProvider provider = tenant -> {
 *     String tenantId = tenant.tenantId();
 *     return ConnectionFactories.get(
 *         ConnectionFactoryOptions.builder()
 *             .option(DRIVER, "postgresql")
 *             .option(HOST, "localhost")
 *             .option(DATABASE, tenantId)
 *             .option(USER, "app_user")
 *             .option(PASSWORD, "secret")
 *             .build()
 *     );
 * };
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see org.axonframework.extension.multitenancy.spring.data.jpa.TenantDataSourceProvider
 */
@FunctionalInterface
public interface TenantConnectionFactoryProvider extends Function<TenantDescriptor, ConnectionFactory> {

    /**
     * Returns a {@link ConnectionFactory} configured for the specified tenant.
     *
     * @param tenant the tenant descriptor identifying the tenant
     * @return a ConnectionFactory for the tenant's database
     * @throws IllegalArgumentException if the tenant is unknown or invalid
     */
    @Override
    ConnectionFactory apply(TenantDescriptor tenant);
}
