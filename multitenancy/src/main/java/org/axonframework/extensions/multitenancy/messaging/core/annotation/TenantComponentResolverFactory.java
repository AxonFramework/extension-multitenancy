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
package org.axonframework.extensions.multitenancy.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Priority;
import org.axonframework.extensions.multitenancy.core.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.core.TenantComponentFactory;
import org.axonframework.extensions.multitenancy.core.TenantComponentRegistry;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ParameterResolverFactory} that creates resolvers for tenant-scoped components.
 * <p>
 * This factory manages multiple {@link TenantComponentRegistry} instances, one for each
 * registered component type. When a handler method has a parameter matching a registered
 * component type, this factory creates a {@link TenantComponentResolver} to provide
 * the tenant-scoped instance.
 * <p>
 * This factory runs with {@link Priority#HIGH} to ensure it processes component parameters
 * before other resolvers that might attempt to inject non-tenant-aware instances.
 * <p>
 * Example registration and usage:
 * <pre>{@code
 * // Registration via MultiTenancyConfigurer
 * MultiTenancyConfigurer.enhance(configurer)
 *     .tenantComponent(OrderRepository.class, tenant -> new InMemoryOrderRepository());
 *
 * // Handler receives tenant-scoped instance
 * @EventHandler
 * public void on(OrderCreatedEvent event, OrderRepository repository) {
 *     repository.save(new OrderProjection(event));
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantComponentResolver
 * @see TenantComponentRegistry
 */
@Priority(Priority.HIGH)
public class TenantComponentResolverFactory implements ParameterResolverFactory {

    private final Map<Class<?>, TenantComponentRegistry<?>> registries = new ConcurrentHashMap<>();
    private final TargetTenantResolver<Message> tenantResolver;

    /**
     * Creates a new {@link TenantComponentResolverFactory}.
     *
     * @param tenantResolver The resolver used to determine which tenant a message belongs to
     */
    public TenantComponentResolverFactory(@Nonnull TargetTenantResolver<Message> tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    /**
     * Registers a component type with its factory.
     * <p>
     * This creates a {@link TenantComponentRegistry} for the component type that will
     * lazily create instances using the provided factory when first accessed.
     *
     * @param componentType The class of the component
     * @param factory       The factory to create component instances per tenant
     * @param <T>           The component type
     * @return The created registry for lifecycle management
     */
    public <T> TenantComponentRegistry<T> registerComponent(@Nonnull Class<T> componentType,
                                                            @Nonnull TenantComponentFactory<T> factory) {
        TenantComponentRegistry<T> registry = new TenantComponentRegistry<>(componentType, factory);
        registries.put(componentType, registry);
        return registry;
    }

    /**
     * Returns the registries managed by this factory.
     * <p>
     * This is used for lifecycle management, allowing the registries to be
     * subscribed to tenant providers.
     *
     * @return An unmodifiable view of the registered component registries
     */
    public Map<Class<?>, TenantComponentRegistry<?>> getRegistries() {
        return Map.copyOf(registries);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public ParameterResolver<?> createInstance(@Nonnull Executable executable,
                                               @Nonnull Parameter[] parameters,
                                               int parameterIndex) {
        Class<?> parameterType = parameters[parameterIndex].getType();

        TenantComponentRegistry<?> registry = registries.get(parameterType);
        if (registry == null) {
            return null;
        }

        return new TenantComponentResolver<>(
                (TenantComponentRegistry<Object>) registry,
                tenantResolver
        );
    }
}
