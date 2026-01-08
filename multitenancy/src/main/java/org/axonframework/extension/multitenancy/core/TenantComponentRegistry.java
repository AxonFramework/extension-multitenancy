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
package org.axonframework.extension.multitenancy.core;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that manages tenant-scoped component instances.
 * <p>
 * This registry caches component instances per tenant, creating them lazily
 * on first access using the provided {@link TenantComponentFactory}. When a
 * tenant is unregistered, its component instance is removed from the cache
 * and cleaned up via {@link TenantComponentFactory#cleanup(TenantDescriptor, Object)}.
 * <p>
 * The registry implements {@link MultiTenantAwareComponent} to participate
 * in tenant lifecycle management, but uses lazy creation - components are
 * only instantiated when first requested, not when a tenant is registered.
 * <p>
 * Components that implement {@link AutoCloseable} are automatically closed
 * when their tenant is removed (unless custom cleanup is provided via the factory).
 *
 * @param <T> The type of component managed by this registry
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantComponentFactory
 */
public class TenantComponentRegistry<T> implements MultiTenantAwareComponent {

    private final Class<T> componentType;
    private final TenantComponentFactory<T> factory;
    private final ConcurrentHashMap<TenantDescriptor, T> components = new ConcurrentHashMap<>();
    private final Set<TenantDescriptor> registeredTenants = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new registry for the given component type and factory.
     *
     * @param componentType The class of the component type for parameter matching
     * @param factory       The factory to create component instances per tenant
     */
    public TenantComponentRegistry(@Nonnull Class<T> componentType,
                                   @Nonnull TenantComponentFactory<T> factory) {
        this.componentType = Objects.requireNonNull(componentType, "Component type must not be null");
        this.factory = Objects.requireNonNull(factory, "Factory must not be null");
    }

    /**
     * Gets the component instance for the given tenant, creating it if necessary.
     * <p>
     * Components are created lazily using the configured factory. Once created,
     * they are cached for subsequent calls with the same tenant.
     *
     * @param tenant The tenant descriptor
     * @return The component instance for this tenant
     */
    public T getComponent(@Nonnull TenantDescriptor tenant) {
        return components.computeIfAbsent(tenant, factory);
    }

    /**
     * Returns the component type managed by this registry.
     *
     * @return The component class
     */
    public Class<T> getComponentType() {
        return componentType;
    }

    /**
     * Returns the set of registered tenants.
     * <p>
     * Note that this returns all tenants that have been registered, not just
     * those for which components have been created. Use this for tenant resolution.
     *
     * @return An unmodifiable view of registered tenant descriptors
     */
    public Set<TenantDescriptor> getTenants() {
        return Set.copyOf(registeredTenants);
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        registeredTenants.add(tenantDescriptor);
        // Lazy creation - don't create component until first access
        return () -> {
            boolean wasRegistered = registeredTenants.remove(tenantDescriptor);
            T removed = components.remove(tenantDescriptor);
            if (removed != null) {
                factory.cleanup(tenantDescriptor, removed);
            }
            return wasRegistered;
        };
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        // For component registries, there's nothing to "start"
        return registerTenant(tenantDescriptor);
    }
}
