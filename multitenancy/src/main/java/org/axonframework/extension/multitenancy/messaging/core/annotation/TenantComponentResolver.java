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
package org.axonframework.extension.multitenancy.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantComponentRegistry;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link ParameterResolver} that resolves parameters to tenant-scoped component instances.
 * <p>
 * This resolver uses the configured {@link TargetTenantResolver} to determine which tenant
 * the message belongs to, then retrieves the component instance from the
 * {@link TenantComponentRegistry}. This enables async-safe multi-tenant component
 * access without ThreadLocal storage.
 * <p>
 * Usage in an event handler:
 * <pre>{@code
 * @EventHandler
 * public void on(OrderCreatedEvent event, OrderRepository repository) {
 *     // Repository is automatically scoped to the event's tenant
 *     repository.save(new OrderProjection(event));
 * }
 * }</pre>
 *
 * @param <T> The type of component this resolver provides
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantComponentRegistry
 * @see TenantComponentResolverFactory
 */
public class TenantComponentResolver<T> implements ParameterResolver<T> {

    private final TenantComponentRegistry<T> registry;
    private final TargetTenantResolver<Message> tenantResolver;

    /**
     * Creates a new {@link TenantComponentResolver}.
     *
     * @param registry       The registry for tenant-scoped components
     * @param tenantResolver The resolver used to determine which tenant a message belongs to
     */
    public TenantComponentResolver(TenantComponentRegistry<T> registry,
                                   TargetTenantResolver<Message> tenantResolver) {
        this.registry = registry;
        this.tenantResolver = tenantResolver;
    }

    @Nonnull
    @Override
    public CompletableFuture<T> resolveParameterValue(@Nonnull ProcessingContext context) {
        Message message = Message.fromContext(context);
        if (message == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No message found in ProcessingContext"));
        }

        try {
            TenantDescriptor tenant = tenantResolver.resolveTenant(message, registry.getTenants());
            T component = registry.getComponent(tenant);
            return CompletableFuture.completedFuture(component);
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
            TenantDescriptor tenant = tenantResolver.resolveTenant(message, registry.getTenants());
            return registry.getTenants().contains(tenant);
        } catch (Exception e) {
            return false;
        }
    }
}
