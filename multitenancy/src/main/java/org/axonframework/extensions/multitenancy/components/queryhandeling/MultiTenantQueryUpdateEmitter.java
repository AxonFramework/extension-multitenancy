package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * @author Stefan Dragisic
 */
public class MultiTenantQueryUpdateEmitter implements QueryUpdateEmitter, MultiTenantAwareComponent {

    private final Map<TenantDescriptor, QueryUpdateEmitter> tenantSegments = new ConcurrentHashMap<>();

    private final List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> dispatchInterceptorsRegistration = new ConcurrentHashMap<>();

    private final Map<TenantDescriptor, List<UpdateHandlerRegistration<?>>> updateHandlersRegistration = new ConcurrentHashMap<>();

    private final Map<TenantDescriptor, Registration> subscribeRegistrations = new ConcurrentHashMap<>();

    private final TenantQueryUpdateEmitterSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<Message<?>> targetTenantResolver;


    public MultiTenantQueryUpdateEmitter(MultiTenantQueryUpdateEmitter.Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    public static MultiTenantQueryUpdateEmitter.Builder builder() {
        return new MultiTenantQueryUpdateEmitter.Builder();
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        tenantSegments.forEach((tenant, bus) ->
                                       dispatchInterceptorsRegistration
                                               .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                               .add(bus.registerDispatchInterceptor(dispatchInterceptor)));

        return () -> dispatchInterceptorsRegistration.values()
                                                     .stream()
                                                     .flatMap(Collection::stream)
                                                     .map(Registration::cancel)
                                                     .reduce((prev, acc) -> prev && acc)
                                                     .orElse(false);
    }

    @Override
    public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         SubscriptionQueryUpdateMessage<U> update) {
        QueryUpdateEmitter tenantEmitter = resolveTenant(update);
        tenantEmitter.emit(filter, update);
    }

    @Override
    public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter, U update) {
        Message<?> message = CurrentUnitOfWork.get().getMessage();
        if (message != null) {
            QueryUpdateEmitter tenantEmitter = resolveTenant(message);
            tenantEmitter.emit(filter, update);
        } else {
            throw new NoSuchTenantException("Can't find any tenant identifier for this message!");
        }
    }

    @Override
    public <Q, U> void emit(Class<Q> queryType, Predicate<? super Q> filter, SubscriptionQueryUpdateMessage<U> update) {
        QueryUpdateEmitter tenantEmitter = resolveTenant(update);
        tenantEmitter.emit(queryType, filter, update);
    }

    @Override
    public <Q, U> void emit(Class<Q> queryType, Predicate<? super Q> filter, U update) {
        Message<?> message = CurrentUnitOfWork.get().getMessage();
        if (message != null) {
            QueryUpdateEmitter tenantEmitter = resolveTenant(message);
            tenantEmitter.emit(queryType, filter, update);
        } else {
            throw new NoSuchTenantException("Can't find any tenant identifier for this message!");
        }
    }

    @Override
    public void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public <Q> void complete(Class<Q> queryType, Predicate<? super Q> filter) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public <Q> void completeExceptionally(Class<Q> queryType, Predicate<? super Q> filter, Throwable cause) {
        throw new UnsupportedOperationException(
                "Invoke operation directly on tenant segment. Use: MultiTenantQueryUpdateEmitter::getTenant");
    }

    @Override
    public boolean queryUpdateHandlerRegistered(SubscriptionQueryMessage<?, ?, ?> query) {
        return tenantSegments.values().stream().anyMatch(segment -> segment.queryUpdateHandlerRegistered(query));
    }


    @Override
    public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query,
                                                                  SubscriptionQueryBackpressure backpressure,
                                                                  int updateBufferSize) {
        return registerUpdateHandler(query, updateBufferSize);
    }

    @Override
    public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query,
                                                                  int updateBufferSize) {
        QueryUpdateEmitter queryUpdateEmitter = resolveTenant(query);
        UpdateHandlerRegistration<U> updateHandlerRegistration = queryUpdateEmitter.registerUpdateHandler(query,
                                                                                                          updateBufferSize);

        updateHandlersRegistration
                .computeIfAbsent(targetTenantResolver.resolveTenant(query, tenantSegments.keySet()),
                                 t -> new CopyOnWriteArrayList<>())
                .add(queryUpdateEmitter.registerUpdateHandler(query, updateBufferSize));

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
        QueryUpdateEmitter tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            QueryUpdateEmitter delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }


    public QueryUpdateEmitter getTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.get(tenantDescriptor);
    }

    public QueryUpdateEmitter unregisterTenant(TenantDescriptor tenantDescriptor) {
        List<UpdateHandlerRegistration<?>> updateHandlerRegistrations = updateHandlersRegistration.remove(
                tenantDescriptor);
        if (updateHandlerRegistrations != null) {
            updateHandlerRegistrations.forEach(it -> it.getRegistration().cancel());
        }

        List<Registration> registrations = dispatchInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) {
            registrations.forEach(Registration::cancel);
        }

        subscribeRegistrations.remove(tenantDescriptor).cancel();
        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerTenantAndSubscribe(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            QueryUpdateEmitter tenantSegment = tenantSegmentFactory.apply(tenant);

            dispatchInterceptors.forEach(handlerInterceptor ->
                                                 dispatchInterceptorsRegistration
                                                         .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                         .add(tenantSegment.registerDispatchInterceptor(
                                                                 handlerInterceptor)));

            return tenantSegment;
        });

        return () -> {
            QueryUpdateEmitter delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    public static class Builder {

        public TenantQueryUpdateEmitterSegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<Message<?>> targetTenantResolver;

        /**
         * @param tenantSegmentFactory
         * @return
         */
        public MultiTenantQueryUpdateEmitter.Builder tenantSegmentFactory(
                TenantQueryUpdateEmitterSegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory,
                                       "The TenantEventProcessorSegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * @param targetTenantResolver
         * @return
         */
        public MultiTenantQueryUpdateEmitter.Builder targetTenantResolver(
                TargetTenantResolver<Message<?>> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        public MultiTenantQueryUpdateEmitter build() {
            return new MultiTenantQueryUpdateEmitter(this);
        }

        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
        }
    }
}
