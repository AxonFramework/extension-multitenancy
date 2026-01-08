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
package org.axonframework.extension.multitenancy.spring.data.jpa;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.extension.multitenancy.core.TenantComponentFactory;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating tenant-scoped Spring Data JPA repository instances.
 * <p>
 * This factory creates repository instances connected to tenant-specific
 * {@link EntityManagerFactory} instances, ensuring that each tenant's data
 * is isolated in its own database.
 * <p>
 * Each tenant's repository operations are wrapped in transactions managed by
 * the tenant-specific {@link TransactionManager}. This follows the same pattern
 * as Axon's {@code JpaEventStorageEngine} which manages its own transactions.
 * <p>
 * Repositories are cached per-tenant for efficiency.
 *
 * @param <T> the repository interface type
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantEntityManagerFactoryBuilder
 * @see TenantTransactionManagerBuilder
 */
public class TenantJpaRepositoryFactory<T> implements TenantComponentFactory<T> {

    private static final Logger logger = LoggerFactory.getLogger(TenantJpaRepositoryFactory.class);

    private final Class<T> repositoryType;
    private final TenantEntityManagerFactoryBuilder emfBuilder;
    private final TenantTransactionManagerBuilder txBuilder;
    private final Map<TenantDescriptor, T> cache = new ConcurrentHashMap<>();

    /**
     * Creates a new factory for the specified repository type.
     *
     * @param repositoryType the repository interface class
     * @param emfBuilder     the tenant EntityManagerFactory builder
     * @param txBuilder      the tenant TransactionManager builder
     */
    public TenantJpaRepositoryFactory(@Nonnull Class<T> repositoryType,
                                      @Nonnull TenantEntityManagerFactoryBuilder emfBuilder,
                                      @Nonnull TenantTransactionManagerBuilder txBuilder) {
        this.repositoryType = repositoryType;
        this.emfBuilder = emfBuilder;
        this.txBuilder = txBuilder;
    }

    /**
     * Creates a factory for the specified repository type.
     *
     * @param repositoryType the repository interface class
     * @param emfBuilder     the tenant EntityManagerFactory builder
     * @param txBuilder      the tenant TransactionManager builder
     * @param <T>            the repository type
     * @return a new factory instance
     */
    public static <T> TenantJpaRepositoryFactory<T> forRepository(
            @Nonnull Class<T> repositoryType,
            @Nonnull TenantEntityManagerFactoryBuilder emfBuilder,
            @Nonnull TenantTransactionManagerBuilder txBuilder) {
        return new TenantJpaRepositoryFactory<>(repositoryType, emfBuilder, txBuilder);
    }

    @Override
    public T apply(TenantDescriptor tenant) {
        return cache.computeIfAbsent(tenant, this::createRepository);
    }

    @SuppressWarnings("unchecked")
    private T createRepository(TenantDescriptor tenant) {
        logger.debug("Creating repository {} for tenant {}", repositoryType.getName(), tenant.tenantId());
        EntityManagerFactory emf = emfBuilder.apply(tenant);
        TransactionManager txManager = txBuilder.apply(tenant);

        logger.debug("Using EMF {} for tenant {}", emf, tenant.tenantId());

        // Create a shared EntityManager proxy that participates in Spring transactions.
        // Each tenant has its own EMF (and database), so data remains isolated.
        EntityManager sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaRepositoryFactory factory = new JpaRepositoryFactory(sharedEntityManager);
        T repository = factory.getRepository(repositoryType);

        logger.debug("Created repository {} for tenant {} with EMF hash {}",
                repositoryType.getSimpleName(), tenant.tenantId(), System.identityHashCode(emf));

        // Wrap the repository in a transactional proxy that manages transactions
        // using the tenant's TransactionManager. This follows the same pattern as
        // JpaEventStorageEngine which manages its own transactions.
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                new TransactionalRepositoryInvocationHandler<>(repository, txManager, tenant.tenantId())
        );
    }

    /**
     * Returns the repository type this factory creates.
     *
     * @return the repository interface class
     */
    public Class<T> getRepositoryType() {
        return repositoryType;
    }

    /**
     * Invocation handler that wraps repository method calls in transactions.
     * <p>
     * This follows the same pattern as Axon's {@code JpaEventStorageEngine}
     * which explicitly manages transactions for JPA operations.
     */
    private static class TransactionalRepositoryInvocationHandler<T> implements InvocationHandler {

        private final T delegate;
        private final TransactionManager transactionManager;
        private final String tenantId;

        TransactionalRepositoryInvocationHandler(T delegate, TransactionManager transactionManager, String tenantId) {
            this.delegate = delegate;
            this.transactionManager = transactionManager;
            this.tenantId = tenantId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle Object methods directly
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }

            logger.debug("Invoking {}.{} for tenant {} with transaction manager {}",
                    delegate.getClass().getSimpleName(), method.getName(), tenantId, transactionManager);

            // Wrap repository operations in a transaction
            var tx = transactionManager.startTransaction();
            try {
                Object result = method.invoke(delegate, args);
                tx.commit();
                logger.debug("Transaction committed for tenant {}", tenantId);
                return result;
            } catch (Throwable t) {
                tx.rollback();
                logger.debug("Transaction rolled back for tenant {}", tenantId, t);
                // Unwrap InvocationTargetException
                if (t instanceof java.lang.reflect.InvocationTargetException) {
                    throw t.getCause();
                }
                throw t;
            }
        }
    }
}
