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
package org.axonframework.extension.multitenancy.messaging.eventhandling.processing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.conversion.Converter;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStoreConfiguration;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link TenantTokenStoreFactory} that creates {@link JdbcTokenStore} instances for each tenant.
 * <p>
 * This factory uses a {@link TenantConnectionProviderFactory} to obtain tenant-specific
 * JDBC connections. Each tenant gets its own {@link JdbcTokenStore} that persists tokens
 * to that tenant's database, ensuring transactional consistency and data isolation.
 * <p>
 * Token stores are cached per tenant to avoid creating multiple instances for the same tenant.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a connection provider factory that returns tenant-specific connections
 * TenantConnectionProviderFactory connectionFactory = tenant -> {
 *     DataSource tenantDataSource = getDataSourceForTenant(tenant);
 *     return tenantDataSource::getConnection;
 * };
 *
 * // Register the factory with configuration
 * configurer.registerComponent(TenantTokenStoreFactory.class, cfg ->
 *     new JdbcTenantTokenStoreFactory(
 *         connectionFactory,
 *         cfg.getComponent(Converter.class),
 *         JdbcTokenStoreConfiguration.DEFAULT
 *     )
 * );
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantTokenStoreFactory
 * @see JdbcTokenStore
 * @see TenantConnectionProviderFactory
 */
public class JdbcTenantTokenStoreFactory implements TenantTokenStoreFactory {

    private final TenantConnectionProviderFactory connectionProviderFactory;
    private final Converter converter;
    private final JdbcTokenStoreConfiguration configuration;
    private final Map<TenantDescriptor, TokenStore> tokenStores = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link JdbcTenantTokenStoreFactory} with the given dependencies.
     *
     * @param connectionProviderFactory The factory for obtaining tenant-specific ConnectionProviders.
     * @param converter                 The converter for serializing/deserializing tokens.
     * @param configuration             The JDBC token store configuration.
     */
    public JdbcTenantTokenStoreFactory(
            @Nonnull TenantConnectionProviderFactory connectionProviderFactory,
            @Nonnull Converter converter,
            @Nonnull JdbcTokenStoreConfiguration configuration
    ) {
        this.connectionProviderFactory = Objects.requireNonNull(connectionProviderFactory,
                "TenantConnectionProviderFactory must not be null");
        this.converter = Objects.requireNonNull(converter, "Converter must not be null");
        this.configuration = Objects.requireNonNull(configuration, "JdbcTokenStoreConfiguration must not be null");
    }

    /**
     * Creates a new {@link JdbcTenantTokenStoreFactory} with default configuration.
     *
     * @param connectionProviderFactory The factory for obtaining tenant-specific ConnectionProviders.
     * @param converter                 The converter for serializing/deserializing tokens.
     */
    public JdbcTenantTokenStoreFactory(
            @Nonnull TenantConnectionProviderFactory connectionProviderFactory,
            @Nonnull Converter converter
    ) {
        this(connectionProviderFactory, converter, JdbcTokenStoreConfiguration.DEFAULT);
    }

    @Override
    public TokenStore apply(TenantDescriptor tenant) {
        return tokenStores.computeIfAbsent(tenant, this::createTokenStore);
    }

    private TokenStore createTokenStore(TenantDescriptor tenant) {
        ConnectionProvider tenantConnectionProvider = connectionProviderFactory.apply(tenant);
        return new JdbcTokenStore(tenantConnectionProvider, converter, configuration);
    }

    /**
     * Returns the number of token stores currently cached.
     * Primarily for testing purposes.
     *
     * @return The number of cached token stores.
     */
    int tokenStoreCount() {
        return tokenStores.size();
    }
}
