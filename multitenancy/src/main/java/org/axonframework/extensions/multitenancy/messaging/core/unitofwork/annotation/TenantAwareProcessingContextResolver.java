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
package org.axonframework.extensions.multitenancy.messaging.core.unitofwork.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.extensions.multitenancy.core.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.extensions.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.extensions.multitenancy.messaging.core.unitofwork.TenantAwareProcessingContext;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ParameterResolver} that resolves {@link ProcessingContext} parameters to
 * {@link TenantAwareProcessingContext} instances when tenant components are registered.
 * <p>
 * This resolver intercepts requests for {@code ProcessingContext} in handler methods and
 * wraps them with tenant-aware functionality. This allows handlers to use
 * {@code context.component(MyType.class)} to retrieve tenant-scoped component instances.
 * <p>
 * If no tenant components are registered or if tenant resolution fails, the original
 * ProcessingContext is returned unchanged.
 * <p>
 * Example usage in a handler:
 * <pre>{@code
 * @EventHandler
 * public void on(OrderCreatedEvent event, ProcessingContext context) {
 *     // Returns tenant-scoped instance based on the event's tenant
 *     OrderRepository repo = context.component(OrderRepository.class);
 *     repo.save(new OrderProjection(event));
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantAwareProcessingContext
 * @see TenantComponentResolverFactory
 */
public class TenantAwareProcessingContextResolver implements ParameterResolver<ProcessingContext> {

    private static final Logger logger = LoggerFactory.getLogger(TenantAwareProcessingContextResolver.class);

    private final TenantComponentResolverFactory resolverFactory;
    private final TargetTenantResolver<Message> tenantResolver;

    /**
     * Creates a new {@link TenantAwareProcessingContextResolver}.
     *
     * @param resolverFactory The factory containing registered tenant component types
     * @param tenantResolver  The resolver used to determine which tenant a message belongs to
     */
    public TenantAwareProcessingContextResolver(
            TenantComponentResolverFactory resolverFactory,
            TargetTenantResolver<Message> tenantResolver
    ) {
        this.resolverFactory = resolverFactory;
        this.tenantResolver = tenantResolver;
    }

    @Nonnull
    @Override
    public CompletableFuture<ProcessingContext> resolveParameterValue(@Nonnull ProcessingContext context) {
        // If no tenant components registered, return original context
        if (resolverFactory.getRegistries().isEmpty()) {
            return CompletableFuture.completedFuture(context);
        }

        // Get message from context to resolve tenant
        Message message = Message.fromContext(context);
        if (message == null) {
            return CompletableFuture.completedFuture(context);
        }

        // Attempt to resolve tenant and wrap context
        try {
            TenantDescriptor tenant = tenantResolver.resolveTenant(
                    message,
                    resolverFactory.getRegistries().values().iterator().next().getTenants()
            );
            return CompletableFuture.completedFuture(
                    new TenantAwareProcessingContext(context, resolverFactory, tenant)
            );
        } catch (Exception e) {
            // Fallback to unwrapped context if tenant resolution fails
            logger.debug("Failed to resolve tenant, returning unwrapped context: {}", e.getMessage());
            return CompletableFuture.completedFuture(context);
        }
    }

    @Override
    public boolean matches(@Nonnull ProcessingContext context) {
        // Always matches - we handle the fallback internally
        return true;
    }
}
