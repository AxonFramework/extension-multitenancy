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
package org.axonframework.extension.multitenancy.messaging.core.unitofwork;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.extension.multitenancy.core.TenantComponentRegistry;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A {@link ProcessingContext} wrapper that intercepts {@link #component(Class)} calls
 * for registered tenant-scoped component types and returns the tenant-specific instance.
 * <p>
 * This allows handlers to retrieve tenant-scoped components via:
 * <pre>{@code
 * @EventHandler
 * void handle(SomeEvent event, ProcessingContext context) {
 *     MyRepository repo = context.component(MyRepository.class);
 *     // repo is the tenant-scoped instance based on the current message's tenant
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public class TenantAwareProcessingContext implements ProcessingContext {

    private final ProcessingContext delegate;
    private final TenantComponentResolverFactory resolverFactory;
    private final TenantDescriptor tenant;

    /**
     * Constructs a tenant-aware {@link ProcessingContext}.
     *
     * @param delegate        The delegate {@link ProcessingContext} to wrap.
     * @param resolverFactory The factory containing registered tenant component types.
     * @param tenant          The tenant descriptor for the current message.
     */
    public TenantAwareProcessingContext(
            @Nonnull ProcessingContext delegate,
            @Nonnull TenantComponentResolverFactory resolverFactory,
            @Nonnull TenantDescriptor tenant
    ) {
        this.delegate = delegate;
        this.resolverFactory = resolverFactory;
        this.tenant = tenant;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <C> C component(@Nonnull Class<C> type) {
        TenantComponentRegistry<?> registry = resolverFactory.getRegistries().get(type);
        if (registry != null) {
            return (C) registry.getComponent(tenant);
        }
        return delegate.component(type);
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <C> C component(@Nonnull Class<C> type, @Nullable String name) {
        TenantComponentRegistry<?> registry = resolverFactory.getRegistries().get(type);
        if (registry != null) {
            return (C) registry.getComponent(tenant);
        }
        return delegate.component(type, name);
    }

    // Delegate all other methods to the wrapped context

    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public boolean isError() {
        return delegate.isError();
    }

    @Override
    public boolean isCommitted() {
        return delegate.isCommitted();
    }

    @Override
    public boolean isCompleted() {
        return delegate.isCompleted();
    }

    @Override
    public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.on(phase, action);
    }

    @Override
    public ProcessingLifecycle runOn(Phase phase, Consumer<ProcessingContext> action) {
        return delegate.runOn(phase, action);
    }

    @Override
    public ProcessingLifecycle onPreInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPreInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnPreInvocation(Consumer<ProcessingContext> action) {
        return delegate.runOnPreInvocation(action);
    }

    @Override
    public ProcessingLifecycle onInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnInvocation(Consumer<ProcessingContext> action) {
        return delegate.runOnInvocation(action);
    }

    @Override
    public ProcessingLifecycle onPostInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPostInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnPostInvocation(Consumer<ProcessingContext> action) {
        return delegate.runOnPostInvocation(action);
    }

    @Override
    public ProcessingLifecycle onPrepareCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPrepareCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnPrepareCommit(Consumer<ProcessingContext> action) {
        return delegate.runOnPrepareCommit(action);
    }

    @Override
    public ProcessingLifecycle onCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnCommit(Consumer<ProcessingContext> action) {
        return delegate.runOnCommit(action);
    }

    @Override
    public ProcessingLifecycle onAfterCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onAfterCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnAfterCommit(Consumer<ProcessingContext> action) {
        return delegate.runOnAfterCommit(action);
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        return delegate.onError(action);
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        return delegate.whenComplete(action);
    }

    @Override
    public ProcessingLifecycle doFinally(Consumer<ProcessingContext> action) {
        return delegate.doFinally(action);
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return delegate.containsResource(key);
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        return delegate.getResource(key);
    }

    @Override
    public Map<ResourceKey<?>, Object> resources() {
        return delegate.resources();
    }

    @Override
    public <T> T putResource(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        return delegate.putResource(key, resource);
    }

    @Override
    public <T> T updateResource(@Nonnull ResourceKey<T> key, @Nonnull UnaryOperator<T> resourceUpdater) {
        return delegate.updateResource(key, resourceUpdater);
    }

    @Override
    public <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull Supplier<T> resourceSupplier) {
        return delegate.computeResourceIfAbsent(key, resourceSupplier);
    }

    @Override
    public <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        return delegate.putResourceIfAbsent(key, resource);
    }

    @Override
    public <T> T removeResource(@Nonnull ResourceKey<T> key) {
        return delegate.removeResource(key);
    }

    @Override
    public <T> boolean removeResource(@Nonnull ResourceKey<T> key, @Nonnull T expectedResource) {
        return delegate.removeResource(key, expectedResource);
    }
}
