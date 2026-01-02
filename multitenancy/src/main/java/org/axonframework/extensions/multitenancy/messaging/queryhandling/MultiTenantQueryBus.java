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
package org.axonframework.extensions.multitenancy.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a {@link QueryBus} that is aware of multiple tenant instances of a {@code QueryBus}. Each
 * {@code QueryBus} instance is considered a "tenant".
 * <p>
 * The {@code MultiTenantQueryBus} relies on a {@link TargetTenantResolver} to dispatch queries via resolved tenant
 * segment of the {@code QueryBus}. {@link TenantQuerySegmentFactory} is a factory to create the tenant segment.
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class MultiTenantQueryBus implements QueryBus, MultiTenantAwareComponent {

    /**
     * The order in which the {@link MultiTenantQueryBus} is applied as a decorator to the {@link QueryBus}.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 50;

    private final Map<QualifiedName, QueryHandler> handlers = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, QueryBus> tenantSegments = new ConcurrentHashMap<>();

    private final TenantQuerySegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<Message> targetTenantResolver;

    /**
     * Instantiate a {@link MultiTenantQueryBus} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantQueryBus} instance with.
     */
    protected MultiTenantQueryBus(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Instantiate a builder to be able to construct a {@link MultiTenantQueryBus}.
     * <p>
     * The {@link TenantQuerySegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @return A Builder to be able to create a {@link MultiTenantQueryBus}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        try {
            return resolveTenant(query).query(query, context);
        } catch (NoSuchTenantException e) {
            return MessageStream.failed(e);
        }
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                  @Nullable ProcessingContext context,
                                                                  int updateBufferSize) {
        try {
            return resolveTenant(query).subscriptionQuery(query, context, updateBufferSize);
        } catch (NoSuchTenantException e) {
            return MessageStream.failed(e);
        }
    }

    @Nonnull
    @Override
    public MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull QueryMessage query,
                                                                             int updateBufferSize) {
        try {
            return resolveTenant(query).subscribeToUpdates(query, updateBufferSize);
        } catch (NoSuchTenantException e) {
            return MessageStream.failed(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<QueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        try {
            return resolveTenantFromContext(context).emitUpdate(filter, updateSupplier, context);
        } catch (NoSuchTenantException | IllegalStateException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<QueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        try {
            return resolveTenantFromContext(context).completeSubscriptions(filter, context);
        } catch (NoSuchTenantException | IllegalStateException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(@Nonnull Predicate<QueryMessage> filter,
                                                                       @Nonnull Throwable cause,
                                                                       @Nullable ProcessingContext context) {
        try {
            return resolveTenantFromContext(context).completeSubscriptionsExceptionally(filter, cause, context);
        } catch (NoSuchTenantException | IllegalStateException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private QueryBus resolveTenantFromContext(@Nullable ProcessingContext context) {
        if (context == null) {
            throw new IllegalStateException(
                    "Cannot resolve tenant for subscription query update: ProcessingContext is required"
            );
        }
        Message message = Message.fromContext(context);
        if (message == null) {
            throw new IllegalStateException(
                    "Cannot resolve tenant for subscription query update: no message found in ProcessingContext"
            );
        }
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(message, tenantSegments.keySet());
        QueryBus tenantQueryBus = tenantSegments.get(tenantDescriptor);
        if (tenantQueryBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantQueryBus;
    }

    @Override
    public QueryBus subscribe(@Nonnull QualifiedName queryName, @Nonnull QueryHandler queryHandler) {
        handlers.computeIfAbsent(queryName, k -> {
            tenantSegments.forEach((tenant, segment) -> segment.subscribe(queryName, queryHandler));
            return queryHandler;
        });
        return this;
    }

    /**
     * Returns the tenant segments managed by this {@code MultiTenantQueryBus}.
     *
     * @return A map of {@link TenantDescriptor} to {@link QueryBus} representing tenant segments.
     */
    public Map<TenantDescriptor, QueryBus> tenantSegments() {
        return tenantSegments;
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

    private QueryBus unregisterTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            QueryBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            handlers.forEach((queryName, queryHandler) -> tenantSegment.subscribe(queryName, queryHandler));

            return tenantSegment;
        });

        return () -> {
            QueryBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    private QueryBus resolveTenant(QueryMessage queryMessage) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(queryMessage, tenantSegments.keySet());
        QueryBus tenantQueryBus = tenantSegments.get(tenantDescriptor);
        if (tenantQueryBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantQueryBus;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("tenantSegments", tenantSegments);
    }

    /**
     * Builder class to instantiate a {@link MultiTenantQueryBus}.
     * <p>
     * The {@link TenantQuerySegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     */
    public static class Builder {

        protected TargetTenantResolver<Message> targetTenantResolver;
        protected TenantQuerySegmentFactory tenantSegmentFactory;

        /**
         * Sets the {@link TenantQuerySegmentFactory} used to build {@link QueryBus} segment for given
         * {@link TenantDescriptor}.
         *
         * @param tenantSegmentFactory A tenant-aware {@link QueryBus} segment factory.
         * @return The current builder instance, for fluent interfacing.
         */
        public Builder tenantSegmentFactory(TenantQuerySegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory, "The TenantQuerySegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve a {@link TenantDescriptor} based on a
         * {@link Message}. Used to find the tenant-specific {@link QueryBus} segment.
         *
         * @param targetTenantResolver The resolver of a {@link TenantDescriptor} based on a {@link Message}. Used
         *                             to find the tenant-specific {@link QueryBus} segment.
         * @return The current builder instance, for fluent interfacing.
         */
        public Builder targetTenantResolver(TargetTenantResolver<Message> targetTenantResolver) {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantQueryBus} as specified through this Builder.
         *
         * @return a {@link MultiTenantQueryBus} as specified through this Builder.
         */
        public MultiTenantQueryBus build() {
            return new MultiTenantQueryBus(this);
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
                          "The TenantQuerySegmentFactory is a hard requirement and should be provided");
        }
    }
}
