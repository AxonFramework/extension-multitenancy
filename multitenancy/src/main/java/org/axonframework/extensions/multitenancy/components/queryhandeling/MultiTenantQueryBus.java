/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.MultiTenantDispatchInterceptorSupport;
import org.axonframework.extensions.multitenancy.components.MultiTenantHandlerInterceptorSupport;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QuerySubscription;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.reactivestreams.Publisher;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.axonframework.common.BuilderUtils.assertNonNull;


/**
 * Implementation of a {@link QueryBus} that is aware of multiple tenant instances of a QueryBus. Each QueryBus instance
 * is considered a "tenant".
 * <p/>
 * The MultiTenantQueryBus relies on a {@link TargetTenantResolver} to dispatch queries via resolved tenant segment of
 * the QueryBus. {@link TenantQuerySegmentFactory} is as factory to create the tenant segment.
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class MultiTenantQueryBus implements QueryBus, MultiTenantAwareComponent,
        MultiTenantDispatchInterceptorSupport<QueryMessage<?, ?>, QueryBus>,
        MultiTenantHandlerInterceptorSupport<QueryMessage<?, ?>, QueryBus> {

    private final Map<TenantDescriptor, QueryBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<String, QuerySubscription<?>> handlers = new ConcurrentHashMap<>();

    private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> dispatchInterceptorsRegistration = new ConcurrentHashMap<>();

    private final List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> handlerInterceptorsRegistration = new ConcurrentHashMap<>();

    private final Map<TenantDescriptor, Registration> subscribeRegistrations = new ConcurrentHashMap<>();

    private final TenantQuerySegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<QueryMessage<?, ?>> targetTenantResolver;

    public MultiTenantQueryBus(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> query) {
        QueryBus tenantQueryBus = resolveTenant(query);
        return tenantQueryBus.query(query);
    }


    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> query, long timeout, TimeUnit unit) {
        QueryBus tenantQueryBus = resolveTenant(query);
        return tenantQueryBus.scatterGather(query, timeout, unit);
    }

    @Override
    public <Q, R> Publisher<QueryResponseMessage<R>> streamingQuery(StreamingQueryMessage<Q, R> query) {
        QueryBus tenantQueryBus = resolveTenant(query);
        return tenantQueryBus.streamingQuery(query);
    }

    @Override
    public <R> Registration subscribe(String queryName, Type responseType,
                                      MessageHandler<? super QueryMessage<?, R>> handler) {
        handlers.computeIfAbsent(queryName, k -> {
            tenantSegments.forEach((tenant, segment) ->
                                           subscribeRegistrations.putIfAbsent(tenant,
                                                                              segment.subscribe(queryName,
                                                                                                responseType,
                                                                                                handler)));
            return new QuerySubscription<>(responseType, handler);
        });
        return () -> subscribeRegistrations.values().stream().map(Registration::cancel).reduce((prev, acc) -> prev
                && acc).orElse(false);
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        QueryBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            QueryBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    public QueryBus unregisterTenant(TenantDescriptor tenantDescriptor) {
        List<Registration> registrations = handlerInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) {
            registrations.forEach(Registration::cancel);
        }

        registrations = dispatchInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) {
            registrations.forEach(Registration::cancel);
        }

        Registration removed = subscribeRegistrations.remove(tenantDescriptor);
        if (removed != null) {
            removed.cancel();
        }

        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            QueryBus tenantSegment = tenantSegmentFactory.apply(tenant);

            dispatchInterceptors.forEach(handlerInterceptor ->
                                                 dispatchInterceptorsRegistration
                                                         .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                         .add(tenantSegment.registerDispatchInterceptor(
                                                                 handlerInterceptor)));

            handlerInterceptors.forEach(handlerInterceptor ->
                                                handlerInterceptorsRegistration
                                                        .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                        .add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

            handlers.forEach((queryName, querySubscription) ->
                                     subscribeRegistrations.putIfAbsent(tenantDescriptor, tenantSegment.subscribe(queryName,
                                                                                                                  querySubscription.getResponseType(),
                                                                                                                  querySubscription.getQueryHandler())));

            return tenantSegment;
        });

        return () -> {
            QueryBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(SubscriptionQueryMessage<Q, I, U> query) {
        return resolveTenant(query)
                .subscriptionQuery(query);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(SubscriptionQueryMessage<Q, I, U> query, int updateBufferSize) {
        return resolveTenant(query)
                .subscriptionQuery(query, updateBufferSize);
    }

    private QueryBus resolveTenant(QueryMessage<?, ?> queryMessage) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(queryMessage, tenantSegments.keySet());
        QueryBus tenantQueryBus = tenantSegments.get(tenantDescriptor);
        if (tenantQueryBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantQueryBus;
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return resolveTenant((QueryMessage<?, ?>) CurrentUnitOfWork.get().getMessage())
                .queryUpdateEmitter();
    }

    /**
     * @param tenantDescriptor for which to get query update emitter
     * @return a query update emitter for the given tenant.
     */
    public QueryUpdateEmitter queryUpdateEmitter(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor)
                             .queryUpdateEmitter();
    }

    @Override
    public Map<TenantDescriptor, QueryBus> tenantSegments() {
        return tenantSegments;
    }

    @Override
    public List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> getHandlerInterceptors() {
        return handlerInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getHandlerInterceptorsRegistration() {
        return handlerInterceptorsRegistration;
    }

    @Override
    public List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> getDispatchInterceptors() {
        return dispatchInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getDispatchInterceptorsRegistration() {
        return dispatchInterceptorsRegistration;
    }

    public static class Builder {

        public TenantQuerySegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<QueryMessage<?, ?>> targetTenantResolver;

        /**
         * Sets the {@link TenantQuerySegmentFactory} used to build {@link QueryBus} segment for given {@link
         * TenantDescriptor}.
         *
         * @param tenantSegmentFactory tenant aware segment factory
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tenantSegmentFactory(TenantQuerySegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory, "The TenantQuerySegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message} message
         *
         * @param targetTenantResolver used to resolve correct tenant segment based on {@link Message} message
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder targetTenantResolver(TargetTenantResolver<QueryMessage<?, ?>> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        public MultiTenantQueryBus build() {
            return new MultiTenantQueryBus(this);
        }

        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            assertNonNull(tenantSegmentFactory, "The TenantQuerySegmentFactory is a hard requirement");
        }
    }
}
