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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.requireNonNull;

/**
 * A simple implementation of {@link TenantProvider} that allows programmatic management of tenants.
 * <p>
 * This provider maintains an in-memory set of tenants and notifies subscribed
 * {@link MultiTenantAwareComponent MultiTenantAwareComponents} when tenants are added or removed.
 * <p>
 * Tenants can be added and removed at runtime, making this suitable for both static and dynamic
 * tenant configurations. For static configurations, tenants can be added during application startup.
 * For dynamic configurations, tenants can be added/removed based on external events.
 * <p>
 * Example usage with static tenants:
 * <pre>{@code
 * SimpleTenantProvider provider = new SimpleTenantProvider();
 * provider.addTenant(TenantDescriptor.tenantWithId("tenant-1"));
 * provider.addTenant(TenantDescriptor.tenantWithId("tenant-2"));
 *
 * MessagingConfigurer messagingConfigurer = MessagingConfigurer.create();
 * // ... configure messaging components ...
 *
 * MultiTenancyConfigurer.enhance(messagingConfigurer)
 *     .registerTenantProvider(config -> provider)
 *     // ... other multi-tenancy configuration
 *     .build();
 * }</pre>
 * <p>
 * Example usage with dynamic tenants:
 * <pre>{@code
 * SimpleTenantProvider provider = new SimpleTenantProvider();
 *
 * // Later, when a new tenant is provisioned:
 * provider.addTenant(TenantDescriptor.tenantWithId("new-tenant"));
 *
 * // When a tenant is decommissioned:
 * provider.removeTenant(TenantDescriptor.tenantWithId("old-tenant"));
 * }</pre>
 * <p>
 * This implementation is thread-safe. Tenant additions and removals can safely occur from any thread.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantProvider
 * @see TenantDescriptor
 * @see MultiTenantAwareComponent
 */
public class SimpleTenantProvider implements TenantProvider {

    private final Set<TenantDescriptor> tenants = ConcurrentHashMap.newKeySet();
    private final List<MultiTenantAwareComponent> subscribers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<TenantDescriptor, List<Registration>> tenantRegistrations = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link SimpleTenantProvider} with no initial tenants.
     */
    public SimpleTenantProvider() {
    }

    /**
     * Creates a new {@link SimpleTenantProvider} with the given initial tenants.
     *
     * @param initialTenants the tenants to register initially
     */
    public SimpleTenantProvider(@Nonnull Collection<TenantDescriptor> initialTenants) {
        requireNonNull(initialTenants, "Initial tenants cannot be null");
        tenants.addAll(initialTenants);
    }

    /**
     * Adds a tenant to this provider and notifies all subscribed components.
     * <p>
     * If the tenant is already registered, this method has no effect.
     * <p>
     * When a tenant is added, all subscribed {@link MultiTenantAwareComponent MultiTenantAwareComponents}
     * are notified via {@link MultiTenantAwareComponent#registerAndStartTenant(TenantDescriptor)}.
     *
     * @param tenant the tenant to add
     * @return {@code true} if the tenant was added, {@code false} if it was already registered
     */
    public boolean addTenant(@Nonnull TenantDescriptor tenant) {
        requireNonNull(tenant, "Tenant cannot be null");
        if (tenants.add(tenant)) {
            List<Registration> registrations = new CopyOnWriteArrayList<>();
            subscribers.forEach(subscriber -> {
                Registration registration = subscriber.registerAndStartTenant(tenant);
                if (registration != null) {
                    registrations.add(registration);
                }
            });
            tenantRegistrations.put(tenant, registrations);
            return true;
        }
        return false;
    }

    /**
     * Adds multiple tenants to this provider and notifies all subscribed components.
     * <p>
     * This is equivalent to calling {@link #addTenant(TenantDescriptor)} for each tenant,
     * but may be more efficient for bulk additions.
     *
     * @param tenantsToAdd the tenants to add
     */
    public void addTenants(@Nonnull Collection<TenantDescriptor> tenantsToAdd) {
        requireNonNull(tenantsToAdd, "Tenants cannot be null");
        tenantsToAdd.forEach(this::addTenant);
    }

    /**
     * Removes a tenant from this provider and triggers cleanup of all tenant-specific resources.
     * <p>
     * If the tenant is not registered, this method has no effect.
     * <p>
     * When a tenant is removed, all {@link Registration} handles returned by
     * {@link MultiTenantAwareComponent#registerTenant(TenantDescriptor)} are cancelled,
     * triggering cleanup of tenant-specific resources such as closing connections,
     * releasing caches, and disposing components.
     *
     * @param tenant the tenant to remove
     * @return {@code true} if the tenant was removed, {@code false} if it was not registered
     */
    public boolean removeTenant(@Nonnull TenantDescriptor tenant) {
        requireNonNull(tenant, "Tenant cannot be null");
        if (tenants.remove(tenant)) {
            List<Registration> registrations = tenantRegistrations.remove(tenant);
            if (registrations != null) {
                registrations.forEach(Registration::cancel);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes a tenant by its ID.
     * <p>
     * This is a convenience method that creates a {@link TenantDescriptor} from the given ID
     * and calls {@link #removeTenant(TenantDescriptor)}.
     *
     * @param tenantId the ID of the tenant to remove
     * @return {@code true} if the tenant was removed, {@code false} if it was not registered
     */
    public boolean removeTenant(@Nonnull String tenantId) {
        requireNonNull(tenantId, "Tenant ID cannot be null");
        return removeTenant(TenantDescriptor.tenantWithId(tenantId));
    }

    /**
     * Checks if a tenant is registered with this provider.
     *
     * @param tenant the tenant to check
     * @return {@code true} if the tenant is registered, {@code false} otherwise
     */
    public boolean hasTenant(@Nonnull TenantDescriptor tenant) {
        requireNonNull(tenant, "Tenant cannot be null");
        return tenants.contains(tenant);
    }

    /**
     * Checks if a tenant with the given ID is registered with this provider.
     *
     * @param tenantId the ID of the tenant to check
     * @return {@code true} if a tenant with the given ID is registered, {@code false} otherwise
     */
    public boolean hasTenant(@Nonnull String tenantId) {
        requireNonNull(tenantId, "Tenant ID cannot be null");
        return hasTenant(TenantDescriptor.tenantWithId(tenantId));
    }

    @Override
    public Registration subscribe(@Nonnull MultiTenantAwareComponent component) {
        requireNonNull(component, "Component cannot be null");
        subscribers.add(component);
        // Register all existing tenants with the new subscriber and store registrations
        tenants.forEach(tenant -> {
            Registration registration = component.registerTenant(tenant);
            if (registration != null) {
                tenantRegistrations.computeIfAbsent(tenant, k -> new CopyOnWriteArrayList<>())
                        .add(registration);
            }
        });
        return () -> subscribers.remove(component);
    }

    @Override
    public List<TenantDescriptor> getTenants() {
        return new ArrayList<>(tenants);
    }
}
