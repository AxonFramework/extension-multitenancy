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
package org.axonframework.extensions.multitenancy.components.commandhandeling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.MultiTenantDispatchInterceptorSupport;
import org.axonframework.extensions.multitenancy.components.MultiTenantHandlerInterceptorSupport;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;


/**
 * Implementation of a {@link CommandBus} that is aware of multiple tenant instances of a {@code CommandBus}. Each
 * {@code CommandBus} instance is considered a "tenant".
 * <p>
 * The {@code MultiTenantCommandBus} relies on a {@link TargetTenantResolver} to dispatch commands via resolved tenant
 * segment of the {@code CommandBus}. {@link TenantCommandSegmentFactory} is as factory to create tenant segments with.
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class MultiTenantCommandBus implements
        CommandBus,
        MultiTenantAwareComponent,
        MultiTenantDispatchInterceptorSupport<CommandMessage<?>, CommandBus>,
        MultiTenantHandlerInterceptorSupport<CommandMessage<?>, CommandBus> {

    private final Map<String, MessageHandler<? super CommandMessage<?>>> handlers = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, CommandBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, Registration> subscribeRegistrations = new ConcurrentHashMap<>();
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> dispatchInterceptorsRegistration = new ConcurrentHashMap<>();
    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> handlerInterceptorsRegistration = new ConcurrentHashMap<>();

    private final TenantCommandSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<CommandMessage<?>> targetTenantResolver;

    /**
     * Instantiate a {@link MultiTenantCommandBus} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiTenantCommandBus} instance with.
     */
    protected MultiTenantCommandBus(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    /**
     * Instantiate a builder to be able to construct a {@link MultiTenantCommandBus}.
     * <p>
     * The {@link TenantCommandSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @return A Builder to be able to create a {@link MultiTenantCommandBus}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <C> void dispatch(@Nonnull CommandMessage<C> command) {
        resolveTenant(command)
                .dispatch(command);
    }

    @Override
    public <C, R> void dispatch(@Nonnull CommandMessage<C> command,
                                @Nonnull CommandCallback<? super C, ? super R> callback) {
        try {
            resolveTenant(command)
                    .dispatch(command, callback);
        } catch (NoSuchTenantException e) {
            callback.onResult(
                    command, GenericCommandResultMessage.asCommandResultMessage(e)
            );
        }
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>> handler) {
        handlers.computeIfAbsent(commandName, k -> {
            tenantSegments.forEach((tenant, segment) -> subscribeRegistrations.putIfAbsent(
                    tenant, segment.subscribe(commandName, handler)
            ));
            return handler;
        });
        return () -> subscribeRegistrations.values()
                                           .stream()
                                           .map(Registration::cancel)
                                           .reduce((prev, acc) -> prev && acc)
                                           .orElse(false);
    }


    @Override
    public Map<TenantDescriptor, CommandBus> tenantSegments() {
        return tenantSegments;
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        CommandBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            CommandBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    private CommandBus unregisterTenant(TenantDescriptor tenantDescriptor) {
        List<Registration> registrations = handlerInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) {
            registrations.forEach(Registration::cancel);
        }

        registrations = dispatchInterceptorsRegistration.remove(tenantDescriptor);
        if (registrations != null) {
            registrations.forEach(Registration::cancel);
        }

        //noinspection resource
        Registration removed = subscribeRegistrations.remove(tenantDescriptor);
        if (removed != null) {
            removed.cancel();
        }

        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, tenant -> {
            CommandBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            dispatchInterceptors.forEach(dispatchInterceptor ->
                                                 dispatchInterceptorsRegistration
                                                         .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                         .add(tenantSegment.registerDispatchInterceptor(
                                                                 dispatchInterceptor)));

            handlerInterceptors.forEach(handlerInterceptor ->
                                                handlerInterceptorsRegistration
                                                        .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                        .add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

            handlers.forEach((commandName, handler) ->
                                     subscribeRegistrations.putIfAbsent(tenantDescriptor,
                                                                        tenantSegment.subscribe(commandName, handler)));

            return tenantSegment;
        });

        return () -> {
            CommandBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    private CommandBus resolveTenant(CommandMessage<?> commandMessage) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(commandMessage, tenantSegments.keySet());
        CommandBus tenantCommandBus = tenantSegments.get(tenantDescriptor);
        if (tenantCommandBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return tenantCommandBus;
    }

    @Override
    public List<MessageDispatchInterceptor<? super CommandMessage<?>>> getDispatchInterceptors() {
        return dispatchInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getDispatchInterceptorsRegistration() {
        return dispatchInterceptorsRegistration;
    }

    @Override
    public List<MessageHandlerInterceptor<? super CommandMessage<?>>> getHandlerInterceptors() {
        return handlerInterceptors;
    }

    @Override
    public Map<TenantDescriptor, List<Registration>> getHandlerInterceptorsRegistration() {
        return handlerInterceptorsRegistration;
    }

    /**
     * Builder class to instantiate a {@link MultiTenantCommandBus}.
     * <p>
     * The {@link TenantCommandSegmentFactory} and {@link TargetTenantResolver} are <b>hard requirements</b> and as such
     * should be provided.
     */
    public static class Builder {

        protected TenantCommandSegmentFactory tenantSegmentFactory;
        protected TargetTenantResolver<CommandMessage<?>> targetTenantResolver;

        /**
         * Sets the {@link TenantCommandSegmentFactory} used to build {@link CommandBus} segment for given
         * {@link TenantDescriptor}.
         *
         * @param tenantSegmentFactory A tenant-aware {@link CommandBus} segment factory.
         * @return The current builder instance, for fluent interfacing.
         */
        public Builder tenantSegmentFactory(TenantCommandSegmentFactory tenantSegmentFactory) {
            assertNonNull(tenantSegmentFactory, "The TenantCommandSegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve a {@link TenantDescriptor} based on a
         * {@link CommandMessage}. Used to find the tenant-specific {@link CommandBus} segment.
         *
         * @param targetTenantResolver The resolver of a {@link TenantDescriptor} based on a {@link CommandMessage}.
         *                             Used to find the tenant-specific {@link CommandBus} segment.
         * @return The current builder instance, for fluent interfacing.
         */
        public Builder targetTenantResolver(TargetTenantResolver<CommandMessage<?>> targetTenantResolver) {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        /**
         * Initializes a {@link MultiTenantCommandBus} as specified through this Builder.
         *
         * @return A {@link MultiTenantCommandBus} as specified through this Builder.
         */
        public MultiTenantCommandBus build() {
            return new MultiTenantCommandBus(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *          *                                    specifications.
         */
        protected void validate() {
            assertNonNull(tenantSegmentFactory,
                          "The TenantCommandSegmentFactory is a hard requirement and should be provided");
            assertNonNull(targetTenantResolver,
                          "The TargetTenantResolver is a hard requirement and should be provided");
        }
    }
}
