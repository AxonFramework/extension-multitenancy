/*
 * Copyright (c) 2010-2023. Axon Framework
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
package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.MultiTenantDispatchInterceptorSupport;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Tenant aware {@link QueryUpdateEmitter} implementation, emitting updates to specific tenants once a
 * {@link TenantDescriptor} can be resolved.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class MultiTenantQueryUpdateEmitter implements
        QueryUpdateEmitter,
        MultiTenantAwareComponent,
        MultiTenantDispatchInterceptorSupport<SubscriptionQueryUpdateMessage<?>, QueryUpdateEmitter> {

    private final Map<TenantDescriptor, QueryUpdateEmitter> tenantSegments = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, List<UpdateHandlerRegistration<?>>> updateHandlersRegistration = new ConcurrentHashMap<>();
    private final List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> dispatchInterceptorsRegistration = new ConcurrentHashMap<>();

    private final TenantQueryUpdateEmitterSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<Message<?>> targetTenantResolver;

    /**
     * Instantiates a {@link MultiTenantQueryUpdateEmitter} based on the given {@code builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantQueryUpdateEmitter} with.
     */
    protected MultiTenantQueryUpdateEmitter(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Instantiate a builder to be able to construct a {@link MultiTenantQueryUpdateEmitter}.
     * <p>
     * The {@link TenantQueryUpdateEmitterSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b>
     * and as such should be provided.
     *
     * @return A Builder to be able to create a {@link MultiTenantQueryUpdateEmitter}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <U> void emit(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         @Nonnull SubscriptionQueryUpdateMessage<U> update) {
        QueryUpdateEmitter tenantEmitter = resolveTenant(update);
        tenantEmitter.emit(filter, update);
    }

    @Override
    public <U> void emit(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         U update) {
        Message<?> message;
        if (update instanceof Message) {
            message = (Message<?>) update;
        } else {
            message = CurrentUnitOfWork.get().getMessage();
        }
        if (message != null) {
            QueryUpdateEmitter tenantEmitter = resolveTenant(message);
            tenantEmitter.emit(filter, update);
        } else {
            throw new NoSuchTenantException("Can't find any tenant identifier for this message!");
        }
    }

    @Override
    public <Q, U> void emit(@Nonnull Class<Q> queryType,
                            @Nonnull Predicate<? super Q> filter,
                            @Nonnull SubscriptionQueryUpdateMessage<U> update) {
        QueryUpdateEmitter tenantEmitter = resolveTenant(update);
        tenantEmitter.emit(queryType, filter, update);
    }

    @Override
    public <Q, U> void emit(@Nonnull Class<Q> queryType,
                            @Nonnull Predicate<? super Q> filter,
                            U update) {
        Message<?> message;
        if (update instanceof Message) {
            message = (Message<?>) update;
        } else {
            message = CurrentUnitOfWork.get().getMessage();
        }
        if (message != null) {
            QueryUpdateEmitter tenantEmitter = resolveTenant(message);
            tenantEmitter.emit(queryType, filter, update);
        } else {
            throw new NoSuchTenantException("Can't find any tenant identifier for this message!");
        }
    }

    @Override
    public void complete(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public <Q> void complete(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public void completeExceptionally(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, ?>> filter,
                                      @Nonnull Throwable cause) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public <Q> void completeExceptionally(@Nonnull Class<Q> queryType,
                                          @Nonnull Predicate<? super Q> filter,
                                          @Nonnull Throwable cause) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public boolean queryUpdateHandlerRegistered(@Nonnull SubscriptionQueryMessage<?, ?, ?> query) {
        return tenantSegments.values().stream().anyMatch(segment -> segment.queryUpdateHandlerRegistered(query));
    }


    @Override
    public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query,
                                                                  SubscriptionQueryBackpressure backpressure,
                                                                  int updateBufferSize) {
        return registerUpdateHandler(query, updateBufferSize);
    }

    @Override
    public <U> UpdateHandlerRegistration<U> registerUpdateHandler(@Nonnull SubscriptionQueryMessage<?, ?, ?> query,
                                                                  int updateBufferSize) {
        QueryUpdateEmitter queryUpdateEmitter = resolveTenant(query);
        UpdateHandlerRegistration<U> updateHandlerRegistration =
                queryUpdateEmitter.registerUpdateHandler(query, updateBufferSize);

        updateHandlersRegistration
                .computeIfAbsent(targetTenantResolver.resolveTenant(query, tenantSegments.keySet()),
                                 t -> new CopyOnWriteArrayList<>())
                .add(updateHandlerRegistration);

        return updateHandlerRegistration;
    }

    @Override
    public Set<SubscriptionQueryMessage<?, ?, ?>> activeSubscriptions() {
        throw new UnsupportedOperationException();
    }

    private QueryUpdateEmitter resolveTenant(Message<?> update) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(update, tenantSegments.keySet());
        QueryUpdateEmitter tenantQueryBus = tenantSegments.get(tenantDescriptor);
        if (tenantQueryBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantQueryBus;
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        return registerAndStartTenant(tenantDescriptor);
    }


    public QueryUpdateEmitter getTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor);
    }

    private QueryUpdateEmitter unregisterTenant(TenantDescriptor tenantDescriptor) {
        List<UpdateHandlerRegistration<?>> updateHandlerRegistrations = updateHandlersRegistration.remove(
                tenantDescriptor);
        if (updateHandlerRegistrations != null) {
            updateHandlerRegistrations.forEach(it -> it.getRegistration().cancel());
        }

        List<Registration> registrations = dispatchInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) {
            registrations.forEach(Registration::cancel);
        }

        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            QueryUpdateEmitter tenantSegment = tenantSegmentFactory.apply(tenant);

            dispatchInterceptors.forEach(dispatchInterceptor ->
                                                 dispatchInterceptorsRegistration
                                                         .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                         .add(tenantSegment.registerDispatchInterceptor(
                                                                 dispatchInterceptor)));

            return tenantSegment;
        });

        return () -> {
            QueryUpdateEmitter delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    @Override
    public Map<TenantDescriptor, QueryUpdateEmitter> tenantSegments() {
        return tenantSegments;
    }

    @Override
    public List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>>> getDispatchInterceptors() {
        return dispatchInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getDispatchInterceptorsRegistration() {
        return dispatchInterceptorsRegistration;
    }

    /**
     * Builder class to instantiate a {@link MultiTenantQueryUpdateEmitter}.
     * <p>
     * The {@link TenantQueryUpdateEmitterSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b>
     * and as such should be provided.
     */
    public static class Builder {

        protected TargetTenantResolver<Message<?>> targetTenantResolver;
        protected TenantQueryUpdateEmitterSegmentFactory tenantSegmentFactory;

        /**
         * Sets the {@link TenantQueryUpdateEmitterSegmentFactory} used to build {@link QueryUpdateEmitter} segment for
         * given {@link TenantDescriptor}.
         *
         * @param tenantSegmentFactory A tenant-aware {@link QueryUpdateEmitter} segment factory.
         * @return The current Builder instance, for fluent interfacing.
         */
        public MultiTenantQueryUpdateEmitter.Builder tenantSegmentFactory(
                TenantQueryUpdateEmitterSegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory,
                          "The TenantQueryUpdateEmitterSegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve a {@link TenantDescriptor} based on a {@link Message}.
         * Used to find the tenant-specific {@link QueryUpdateEmitter} segment.
         *
         * @param targetTenantResolver The resolver of a {@link TenantDescriptor} based on a {@link Message}. Used to
         *                             find the tenant-specific {@link QueryUpdateEmitter} segment.
         * @return The current builder instance, for fluent interfacing.
         */
        public MultiTenantQueryUpdateEmitter.Builder targetTenantResolver(
                TargetTenantResolver<Message<?>> targetTenantResolver
        ) {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantQueryUpdateEmitter} as specified through this Builder.
         *
         * @return a {@link MultiTenantQueryUpdateEmitter} as specified through this Builder.
         */
        public MultiTenantQueryUpdateEmitter build() {
            return new MultiTenantQueryUpdateEmitter(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(targetTenantResolver,
                          "The TargetTenantResolver is a hard requirement and should be provided");
            assertNonNull(tenantSegmentFactory,
                          "The TenantQueryUpdateEmitterSegmentFactory is a hard requirement and should be provided");
        }
    }
}
