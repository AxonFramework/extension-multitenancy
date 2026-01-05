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
import jakarta.annotation.Nullable;
import org.axonframework.common.Priority;
import org.axonframework.extensions.multitenancy.core.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * A {@link ParameterResolverFactory} that creates {@link TenantAwareProcessingContextResolver}
 * instances for handler parameters of type {@link ProcessingContext}.
 * <p>
 * This factory runs with {@link Priority#HIGH} to ensure it processes {@code ProcessingContext}
 * parameters before the default resolver, allowing tenant-aware wrapping of the context.
 * <p>
 * When a handler method has a {@code ProcessingContext} parameter, this factory creates a
 * resolver that wraps the context with {@link org.axonframework.extensions.multitenancy.messaging.core.unitofwork.TenantAwareProcessingContext},
 * enabling {@code context.component(MyType.class)} to return tenant-scoped instances.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantAwareProcessingContextResolver
 * @see TenantComponentResolverFactory
 */
@Priority(Priority.HIGH)
public class TenantAwareProcessingContextResolverFactory implements ParameterResolverFactory {

    private final TenantComponentResolverFactory tenantComponentFactory;
    private final TargetTenantResolver<Message> tenantResolver;

    /**
     * Creates a new {@link TenantAwareProcessingContextResolverFactory}.
     *
     * @param tenantComponentFactory The factory containing registered tenant component types
     * @param tenantResolver         The resolver used to determine which tenant a message belongs to
     */
    public TenantAwareProcessingContextResolverFactory(
            @Nonnull TenantComponentResolverFactory tenantComponentFactory,
            @Nonnull TargetTenantResolver<Message> tenantResolver
    ) {
        this.tenantComponentFactory = tenantComponentFactory;
        this.tenantResolver = tenantResolver;
    }

    @Nullable
    @Override
    public ParameterResolver<?> createInstance(@Nonnull Executable executable,
                                               @Nonnull Parameter[] parameters,
                                               int parameterIndex) {
        if (ProcessingContext.class.isAssignableFrom(parameters[parameterIndex].getType())) {
            return new TenantAwareProcessingContextResolver(tenantComponentFactory, tenantResolver);
        }
        return null;
    }
}
