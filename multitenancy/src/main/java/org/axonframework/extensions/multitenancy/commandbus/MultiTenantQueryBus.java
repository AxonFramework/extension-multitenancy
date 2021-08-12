package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/*
@author Steven van Beelen
@author Stefan Dragisic
 */

public class MultiTenantQueryBus implements QueryBus {

    private final Map<TenantDescriptor, QueryBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, MessageHandler<? super QueryMessage<?, ?>>> handlers = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Registration>> tenantRegistrations = new ConcurrentHashMap<>();

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


    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        QueryBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            QueryBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    public QueryBus unregisterTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.remove(tenantDescriptor);
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

    private QueryBus resolveTenant(QueryMessage<?, ?> queryMessage) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(queryMessage, tenantSegments.keySet());
        QueryBus tenantQueryBus = tenantSegments.get(tenantDescriptor);
        if (tenantQueryBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantQueryBus;
    }

    @Override
    public <R> Registration subscribe(String queryName, Type responseType, MessageHandler<? super QueryMessage<?, R>> handler) {
        Map<String, Registration> registrationMap = tenantSegments.entrySet()
                .stream()
                .collect(Collectors.toMap(key -> key.getKey().tenantId(), entry -> entry.getValue().subscribe(queryName, responseType, handler)));

        //todo add too tenantRegistrations

        return () -> {
            //todo iterate registrationMap and cancel all
            return true;
        };
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return null;
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super QueryMessage<?, ?>> handlerInterceptor) {
        return null;
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(SubscriptionQueryMessage<Q, I, U> query) {
        QueryBus tenantQueryBus = resolveTenant(query);
        return tenantQueryBus.subscriptionQuery(query);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(SubscriptionQueryMessage<Q, I, U> query, int updateBufferSize) {
        QueryBus tenantQueryBus = resolveTenant(query);
        return tenantQueryBus.subscriptionQuery(query, updateBufferSize);
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return null;
    }

    public static class Builder {

        public TenantQuerySegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<QueryMessage<?, ?>> targetTenantResolver;

        /**
         * @param tenantSegmentFactory
         * @return
         */
        public Builder tenantSegmentFactory(TenantQuerySegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory, "");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * @param targetTenantResolver
         * @return
         */
        public Builder targetTenantResolver(TargetTenantResolver<QueryMessage<?, ?>> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        public MultiTenantQueryBus build() {
            return new MultiTenantQueryBus(this);
        }

        protected void validate() {
            // todo
        }
    }
}
