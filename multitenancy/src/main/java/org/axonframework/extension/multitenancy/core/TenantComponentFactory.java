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

import java.util.function.Function;

/**
 * Factory for creating and cleaning up tenant-scoped component instances.
 * <p>
 * Users implement this interface to define how components (such as repositories,
 * services, or any other dependencies) are created for each tenant. The factory
 * is invoked lazily when a component is first needed for a tenant.
 * <p>
 * When a tenant is removed, the {@link #cleanup(TenantDescriptor, Object)} method
 * is called to release any resources held by the component. By default, this method
 * will close components that implement {@link AutoCloseable}.
 * <p>
 * Example usage:
 * <pre>{@code
 * TenantComponentFactory<CourseRepository> repoFactory =
 *     tenant -> new InMemoryCourseRepository();
 *
 * // Or with tenant-specific configuration:
 * TenantComponentFactory<CourseRepository> repoFactory =
 *     tenant -> new JpaCourseRepository(getDataSourceForTenant(tenant));
 *
 * // For components needing custom cleanup:
 * TenantComponentFactory<EntityManagerFactory> emfFactory =
 *     new TenantComponentFactory<>() {
 *         public EntityManagerFactory apply(TenantDescriptor tenant) {
 *             return createEntityManagerFactory(tenant);
 *         }
 *         public void cleanup(TenantDescriptor tenant, EntityManagerFactory emf) {
 *             emf.close();
 *             logger.info("Closed EMF for tenant {}", tenant.tenantId());
 *         }
 *     };
 * }</pre>
 *
 * @param <T> The type of component this factory creates
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantComponentRegistry
 */
@FunctionalInterface
public interface TenantComponentFactory<T> extends Function<TenantDescriptor, T> {

    /**
     * Creates a component instance for the given tenant.
     *
     * @param tenant The tenant descriptor identifying which tenant needs the component
     * @return A new component instance for this tenant
     */
    @Override
    T apply(TenantDescriptor tenant);

    /**
     * Cleans up a component instance when a tenant is removed.
     * <p>
     * This method is called when a tenant is unregistered from the system. The default
     * implementation will close components that implement {@link AutoCloseable}. Override
     * this method to provide custom cleanup logic for components that require special
     * handling (e.g., releasing database connections, flushing caches, etc.).
     * <p>
     * Any exceptions thrown during cleanup are logged but do not propagate, ensuring
     * that cleanup of other components can continue.
     *
     * @param tenant    The tenant being removed
     * @param component The component instance to clean up
     */
    default void cleanup(TenantDescriptor tenant, T component) {
        if (component instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                // Log but don't propagate - cleanup should be best-effort
                org.slf4j.LoggerFactory.getLogger(TenantComponentFactory.class)
                        .warn("Error closing AutoCloseable component for tenant [{}]: {}",
                                tenant.tenantId(), e.getMessage(), e);
            }
        }
    }
}
