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
import org.axonframework.common.Priority;
import org.axonframework.extensions.multitenancy.core.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A {@link ParameterResolverFactory} that resolves Spring Data JPA repository parameters
 * as tenant-scoped instances.
 * <p>
 * When a message handler declares a Spring Data {@link Repository} as a parameter, this factory
 * automatically provides a tenant-specific instance based on the message's tenant metadata.
 * Each tenant gets its own repository instance backed by a tenant-specific {@link EntityManagerFactory}.
 * <p>
 * This factory is automatically registered when {@link TenantDataSourceProvider} is configured
 * and Spring Data JPA is on the classpath.
 * <p>
 * Example usage in a handler:
 * <pre>{@code
 * @EventHandler
 * void on(OrderCreatedEvent event, OrderRepository repository) {
 *     // repository is automatically scoped to the event's tenant
 *     repository.save(new OrderProjection(event));
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantDataSourceProvider
 * @see TenantEntityManagerFactoryBuilder
 */
@Priority(Priority.HIGH)
public class TenantRepositoryParameterResolverFactory implements ParameterResolverFactory {

    private static final Logger logger = LoggerFactory.getLogger(TenantRepositoryParameterResolverFactory.class);

    private final TargetTenantResolver<Message> tenantResolver;
    private final Function<TenantDescriptor, EntityManagerFactory> emfProvider;
    private final Set<Class<?>> repositoryTypes;

    // Cache: repositoryType -> (tenant -> repository instance)
    private final Map<Class<?>, Map<TenantDescriptor, Object>> repositoryCache = new ConcurrentHashMap<>();

    /**
     * Creates a new factory for resolving tenant-scoped Spring Data repositories.
     *
     * @param tenantResolver  resolver for extracting tenant from messages
     * @param emfProvider     provider for tenant-specific EntityManagerFactories
     * @param repositoryTypes the repository interface types to resolve
     */
    public TenantRepositoryParameterResolverFactory(
            @Nonnull TargetTenantResolver<Message> tenantResolver,
            @Nonnull Function<TenantDescriptor, EntityManagerFactory> emfProvider,
            @Nonnull Set<Class<?>> repositoryTypes) {
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "Tenant resolver may not be null");
        this.emfProvider = Objects.requireNonNull(emfProvider, "EMF provider may not be null");
        this.repositoryTypes = Objects.requireNonNull(repositoryTypes, "Repository types may not be null");

        logger.debug("Initialized TenantRepositoryParameterResolverFactory for {} repository types",
                repositoryTypes.size());
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        Parameter parameter = parameters[parameterIndex];
        Class<?> parameterType = parameter.getType();

        // Check if this parameter is a repository we handle
        if (Repository.class.isAssignableFrom(parameterType) && repositoryTypes.contains(parameterType)) {
            logger.debug("Creating tenant-scoped resolver for repository parameter: {}", parameterType.getName());
            return new TenantRepositoryParameterResolver(parameterType);
        }

        return null;
    }

    /**
     * Parameter resolver that provides tenant-scoped repository instances.
     */
    private class TenantRepositoryParameterResolver implements ParameterResolver<Object> {

        private final Class<?> repositoryType;

        TenantRepositoryParameterResolver(Class<?> repositoryType) {
            this.repositoryType = repositoryType;
        }

        @Nonnull
        @Override
        public CompletableFuture<Object> resolveParameterValue(@Nonnull ProcessingContext context) {
            Message message = Message.fromContext(context);
            if (message == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No message found in ProcessingContext"));
            }

            try {
                TenantDescriptor tenant = tenantResolver.resolveTenant(message, Collections.emptyList());
                Object repository = getOrCreateRepository(repositoryType, tenant);
                return CompletableFuture.completedFuture(repository);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            Message message = Message.fromContext(context);
            if (message == null) {
                return false;
            }
            try {
                TenantDescriptor tenant = tenantResolver.resolveTenant(message, Collections.emptyList());
                return tenant != null;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrCreateRepository(Class<T> repositoryType, TenantDescriptor tenant) {
        Map<TenantDescriptor, Object> tenantCache = repositoryCache.computeIfAbsent(
                repositoryType, k -> new ConcurrentHashMap<>());

        return (T) tenantCache.computeIfAbsent(tenant, t -> createRepository(repositoryType, t));
    }

    private Object createRepository(Class<?> repositoryType, TenantDescriptor tenant) {
        logger.debug("Creating repository {} for tenant {}", repositoryType.getName(), tenant.tenantId());

        EntityManagerFactory emf = emfProvider.apply(tenant);
        JpaRepositoryFactory factory = new JpaRepositoryFactory(emf.createEntityManager());
        return factory.getRepository(repositoryType);
    }
}
