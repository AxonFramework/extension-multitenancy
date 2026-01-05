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
package org.axonframework.extension.multitenancy.spring;

import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.slf4j.LoggerFactory;

/**
 * Interface for defining tenant-scoped components that can be automatically discovered
 * and registered by Spring auto-configuration.
 * <p>
 * Classes implementing this interface will be:
 * <ul>
 *     <li>Automatically discovered via classpath scanning</li>
 *     <li>Instantiated with Spring dependency injection (but NOT registered as Spring beans)</li>
 *     <li>Registered as tenant components for injection into message handlers</li>
 * </ul>
 * <p>
 * The implementing class acts as a factory that creates tenant-specific instances.
 * Spring will inject dependencies into the factory instance, which can then pass
 * those dependencies to the tenant-specific instances it creates.
 * <p>
 * <strong>Important:</strong> Classes implementing this interface should NOT be annotated
 * with {@code @Component} or any other Spring stereotype annotation. They will receive
 * Spring dependency injection through {@code AutowireCapableBeanFactory.createBean()},
 * but will not be registered in the Spring application context. This ensures that
 * the component type cannot be accidentally autowired outside of message handlers.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Note: No @Component annotation!
 * public class TenantAwareOrderService implements TenantComponent<TenantAwareOrderService> {
 *     private final EmailService emailService; // Spring dependency
 *     private final String tenantId;
 *
 *     // Constructor for factory instance - Spring injects EmailService
 *     public TenantAwareOrderService(EmailService emailService) {
 *         this.emailService = emailService;
 *         this.tenantId = null;
 *     }
 *
 *     // Private constructor for tenant-specific instances
 *     private TenantAwareOrderService(EmailService emailService, String tenantId) {
 *         this.emailService = emailService;
 *         this.tenantId = tenantId;
 *     }
 *
 *     @Override
 *     public TenantAwareOrderService createForTenant(TenantDescriptor tenant) {
 *         return new TenantAwareOrderService(emailService, tenant.tenantId());
 *     }
 *
 *     public void processOrder(Order order) {
 *         // Business logic using tenantId and emailService
 *     }
 * }
 *
 * // In a message handler - automatically receives tenant-specific instance:
 * @EventHandler
 * public void handle(OrderPlaced event, TenantAwareOrderService orderService) {
 *     orderService.processOrder(event.getOrder());
 * }
 * }</pre>
 *
 * @param <T> the type of component this factory creates, typically the implementing class itself
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see org.axonframework.extension.multitenancy.core.TenantDescriptor
 * @see org.axonframework.extension.multitenancy.core.TenantComponentFactory
 */
public interface TenantComponent<T> {

    /**
     * Creates a component instance configured for the specified tenant.
     * <p>
     * This method is called lazily when a tenant-specific instance is first needed.
     * The returned instance will be cached per-tenant, so subsequent requests for
     * the same tenant will return the same instance.
     * <p>
     * Implementations should create a new instance configured with the tenant's context,
     * typically by passing the tenant information and any dependencies held by the
     * factory instance.
     *
     * @param tenant the tenant descriptor identifying the tenant
     * @return a new component instance configured for the specified tenant
     */
    T createForTenant(TenantDescriptor tenant);

    /**
     * Called when a tenant is unregistered to perform cleanup of tenant-specific resources.
     * <p>
     * The default implementation handles {@link AutoCloseable} components by calling
     * {@link AutoCloseable#close()}. Override this method to provide custom cleanup logic.
     *
     * @param tenant    the tenant descriptor identifying the tenant being removed
     * @param component the component instance to clean up
     */
    default void cleanupForTenant(TenantDescriptor tenant, T component) {
        if (component instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                LoggerFactory.getLogger(TenantComponent.class)
                        .warn("Error closing AutoCloseable component for tenant [{}]: {}",
                                tenant.tenantId(), e.getMessage(), e);
            }
        }
    }
}
