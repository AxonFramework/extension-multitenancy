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
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.conversion.Converter;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.JpaTokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.JpaTokenStoreConfiguration;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A {@link TenantTokenStoreFactory} that creates {@link JpaTokenStore} instances for each tenant.
 * <p>
 * This factory uses a provider function to obtain tenant-specific {@link EntityManagerFactory} instances.
 * Each tenant gets its own {@link JpaTokenStore} that persists tokens to that tenant's database,
 * ensuring transactional consistency and data isolation.
 * <p>
 * Token stores are cached per tenant to avoid creating multiple instances for the same tenant.
 * <p>
 * Example usage with {@code tenantComponent()}:
 * <pre>{@code
 * // Register EntityManagerFactory as a tenant component
 * MultiTenancyConfigurer.enhance(configurer)
 *     .tenantComponent(EntityManagerFactory.class, tenant -> createEmfForTenant(tenant));
 *
 * // Create the token store factory using the tenant component
 * JpaTenantTokenStoreFactory tokenStoreFactory = new JpaTenantTokenStoreFactory(
 *     tenant -> config.getComponent(TenantComponentRegistry.class)
 *                     .getComponent(EntityManagerFactory.class, tenant),
 *     converter
 * );
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantTokenStoreFactory
 * @see JpaTokenStore
 */
public class JpaTenantTokenStoreFactory implements TenantTokenStoreFactory {

    private final Function<TenantDescriptor, EntityManagerFactory> emfProvider;
    private final Converter converter;
    private final JpaTokenStoreConfiguration configuration;
    private final Map<TenantDescriptor, TokenStore> tokenStores = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link JpaTenantTokenStoreFactory} with the given dependencies.
     *
     * @param emfProvider   Function that provides an {@link EntityManagerFactory} for each tenant.
     * @param converter     The converter for serializing/deserializing tokens.
     * @param configuration The JPA token store configuration.
     */
    public JpaTenantTokenStoreFactory(
            @Nonnull Function<TenantDescriptor, EntityManagerFactory> emfProvider,
            @Nonnull Converter converter,
            @Nonnull JpaTokenStoreConfiguration configuration
    ) {
        this.emfProvider = Objects.requireNonNull(emfProvider,
                "EntityManagerFactory provider must not be null");
        this.converter = Objects.requireNonNull(converter, "Converter must not be null");
        this.configuration = Objects.requireNonNull(configuration, "JpaTokenStoreConfiguration must not be null");
    }

    /**
     * Creates a new {@link JpaTenantTokenStoreFactory} with default configuration.
     *
     * @param emfProvider Function that provides an {@link EntityManagerFactory} for each tenant.
     * @param converter   The converter for serializing/deserializing tokens.
     */
    public JpaTenantTokenStoreFactory(
            @Nonnull Function<TenantDescriptor, EntityManagerFactory> emfProvider,
            @Nonnull Converter converter
    ) {
        this(emfProvider, converter, JpaTokenStoreConfiguration.DEFAULT);
    }

    @Override
    public TokenStore apply(TenantDescriptor tenant) {
        return tokenStores.computeIfAbsent(tenant, this::createTokenStore);
    }

    private TokenStore createTokenStore(TenantDescriptor tenant) {
        EntityManagerFactory emf = emfProvider.apply(tenant);
        // Create an EntityManagerProvider that creates fresh EntityManagers from the factory
        EntityManagerProvider tenantEntityManagerProvider = emf::createEntityManager;
        return new JpaTokenStore(tenantEntityManagerProvider, converter, configuration);
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
