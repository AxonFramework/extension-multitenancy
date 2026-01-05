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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.Repository;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Factory for creating tenant-scoped Spring Data JPA repository instances.
 * <p>
 * This class creates repository instances bound to tenant-specific {@link EntityManagerFactory}
 * instances. Each tenant gets its own repository instance that operates on its own database.
 * <p>
 * Repository instances are cached per tenant to ensure that the same tenant always receives
 * the same repository instance.
 * <p>
 * Example usage with {@code MultiTenancyConfigurer}:
 * <pre>{@code
 * // Create EMF builder
 * TenantEntityManagerFactoryBuilder emfBuilder = TenantEntityManagerFactoryBuilder
 *     .forDataSourceProvider(tenantDataSourceProvider)
 *     .packagesToScan("com.example.domain")
 *     .build();
 *
 * // Create repository factory
 * TenantRepositoryFactory<CustomerRepository> repoFactory =
 *     TenantRepositoryFactory.forRepository(CustomerRepository.class, emfBuilder);
 *
 * // Register with multi-tenancy configurer
 * multiTenancyConfigurer.tenantComponent(CustomerRepository.class, repoFactory);
 * }</pre>
 *
 * @param <T> the repository interface type
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantEntityManagerFactoryBuilder
 * @see TenantDataSourceProvider
 */
public class TenantRepositoryFactory<T extends Repository<?, ?>> implements Function<TenantDescriptor, T> {

    private final Class<T> repositoryInterface;
    private final Function<TenantDescriptor, EntityManagerFactory> emfProvider;
    private final ConcurrentHashMap<TenantDescriptor, T> repositoryCache = new ConcurrentHashMap<>();

    private TenantRepositoryFactory(Class<T> repositoryInterface,
                                    Function<TenantDescriptor, EntityManagerFactory> emfProvider) {
        this.repositoryInterface = Objects.requireNonNull(repositoryInterface,
                "Repository interface may not be null");
        this.emfProvider = Objects.requireNonNull(emfProvider,
                "EntityManagerFactory provider may not be null");
    }

    /**
     * Creates a new factory for the specified repository interface.
     *
     * @param repositoryInterface the Spring Data repository interface
     * @param emfProvider         provider function for tenant-specific EntityManagerFactories
     * @param <T>                 the repository interface type
     * @return a new TenantRepositoryFactory
     */
    public static <T extends Repository<?, ?>> TenantRepositoryFactory<T> forRepository(
            @Nonnull Class<T> repositoryInterface,
            @Nonnull Function<TenantDescriptor, EntityManagerFactory> emfProvider) {
        return new TenantRepositoryFactory<>(repositoryInterface, emfProvider);
    }

    /**
     * Returns the repository instance for the specified tenant, creating it if necessary.
     * <p>
     * Created repository instances are cached to ensure that the same tenant always
     * receives the same instance.
     *
     * @param tenant the tenant descriptor
     * @return the repository instance for the tenant
     */
    @Override
    public T apply(TenantDescriptor tenant) {
        return repositoryCache.computeIfAbsent(tenant, this::createRepository);
    }

    /**
     * Returns the number of cached repository instances.
     *
     * @return the cache size
     */
    public int cacheSize() {
        return repositoryCache.size();
    }

    /**
     * Returns the repository interface this factory creates instances for.
     *
     * @return the repository interface class
     */
    public Class<T> getRepositoryInterface() {
        return repositoryInterface;
    }

    private T createRepository(TenantDescriptor tenant) {
        EntityManagerFactory emf = emfProvider.apply(tenant);
        EntityManager entityManager = emf.createEntityManager();
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(repositoryInterface);
    }
}
