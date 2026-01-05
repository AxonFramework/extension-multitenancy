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
import org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Builder for creating tenant-specific {@link TransactionManager} instances.
 * <p>
 * Each tenant's {@link EntityManagerFactory} requires its own {@link JpaTransactionManager}
 * to properly manage transactions. This builder creates and caches per-tenant
 * {@link SpringTransactionManager} instances that wrap tenant-specific {@code JpaTransactionManager}s.
 * <p>
 * This follows the established Axon Framework pattern where:
 * <ul>
 *     <li>{@code JpaTransactionManager} (Spring) is bound to a specific {@code EntityManagerFactory}</li>
 *     <li>{@code SpringTransactionManager} (Axon) wraps Spring's {@code PlatformTransactionManager}</li>
 *     <li>Each tenant gets its own transaction manager for proper database isolation</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * TenantTransactionManagerBuilder txBuilder = TenantTransactionManagerBuilder
 *     .forEntityManagerFactoryBuilder(tenantEmfBuilder)
 *     .build();
 *
 * // Get transaction manager for a specific tenant
 * TransactionManager txManager = txBuilder.apply(tenant);
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantEntityManagerFactoryBuilder
 * @see SpringTransactionManager
 */
public class TenantTransactionManagerBuilder implements Function<TenantDescriptor, TransactionManager> {

    private static final Logger logger = LoggerFactory.getLogger(TenantTransactionManagerBuilder.class);

    private final TenantEntityManagerFactoryBuilder emfBuilder;
    private final Map<TenantDescriptor, TransactionManager> cache = new ConcurrentHashMap<>();

    private TenantTransactionManagerBuilder(Builder builder) {
        this.emfBuilder = builder.emfBuilder;
    }

    /**
     * Creates a new builder with the specified {@link TenantEntityManagerFactoryBuilder}.
     *
     * @param emfBuilder the provider for tenant-specific EntityManagerFactories
     * @return a new builder instance
     */
    public static Builder forEntityManagerFactoryBuilder(@Nonnull TenantEntityManagerFactoryBuilder emfBuilder) {
        return new Builder(emfBuilder);
    }

    /**
     * Returns the {@link TransactionManager} for the specified tenant, creating it if necessary.
     * <p>
     * Created TransactionManager instances are cached to ensure that the same tenant
     * always receives the same instance.
     *
     * @param tenant the tenant descriptor
     * @return the TransactionManager for the tenant
     */
    @Override
    public TransactionManager apply(TenantDescriptor tenant) {
        return cache.computeIfAbsent(tenant, this::createTransactionManager);
    }

    private TransactionManager createTransactionManager(TenantDescriptor tenant) {
        EntityManagerFactory emf = emfBuilder.apply(tenant);
        logger.debug("Creating TransactionManager for tenant {} with EMF hash {}", tenant.tenantId(), System.identityHashCode(emf));
        JpaTransactionManager jpaTransactionManager = new JpaTransactionManager(emf);
        // Initialize the transaction manager
        jpaTransactionManager.afterPropertiesSet();
        return new SpringTransactionManager(jpaTransactionManager);
    }

    /**
     * Returns the number of cached TransactionManager instances.
     *
     * @return the cache size
     */
    public int cacheSize() {
        return cache.size();
    }

    /**
     * Builder for {@link TenantTransactionManagerBuilder}.
     */
    public static class Builder {

        private final TenantEntityManagerFactoryBuilder emfBuilder;

        private Builder(TenantEntityManagerFactoryBuilder emfBuilder) {
            this.emfBuilder = Objects.requireNonNull(emfBuilder,
                    "TenantEntityManagerFactoryBuilder may not be null");
        }

        /**
         * Builds the {@link TenantTransactionManagerBuilder}.
         *
         * @return a new TenantTransactionManagerBuilder instance
         */
        public TenantTransactionManagerBuilder build() {
            return new TenantTransactionManagerBuilder(this);
        }
    }
}
