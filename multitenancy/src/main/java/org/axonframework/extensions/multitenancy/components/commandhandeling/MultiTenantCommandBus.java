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

package org.axonframework.extensions.multitenancy.components.commandhandeling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.axonframework.common.BuilderUtils.assertNonNull;


/**
 * Implementation of a {@link CommandBus} that is aware of multiple tenant instances of a CommandBus. Each CommandBus
 * instance is considered a "tenant".
 * <p/>
 * The MultiTenantCommandBus relies on a {@link TargetTenantResolver} to dispatch commands via resolved tenant segment
 * of the CommandBus. {@link TenantCommandSegmentFactory} is as factory to create the tenant segment.
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 */

public class MultiTenantCommandBus implements CommandBus, MultiTenantAwareComponent {

    private final Map<TenantDescriptor, CommandBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<String, MessageHandler<? super CommandMessage<?>>> handlers = new ConcurrentHashMap<>();

    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> dispatchInterceptorsRegistration = new ConcurrentHashMap<>();

    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final Map<TenantDescriptor, List<Registration>> handlerInterceptorsRegistration = new ConcurrentHashMap<>();

    private final Map<TenantDescriptor, Registration> subscribeRegistrations = new ConcurrentHashMap<>();

    private final TenantCommandSegmentFactory tenantSegmentFactory;
    private final TargetTenantResolver<CommandMessage<?>> targetTenantResolver;


    public MultiTenantCommandBus(Builder builder) {
        builder.validate();
        this.tenantSegmentFactory = builder.tenantSegmentFactory;
        this.targetTenantResolver = builder.targetTenantResolver;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        resolveTenant(command)
                .dispatch(command);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
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
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        handlers.computeIfAbsent(commandName, k -> {
            tenantSegments.forEach((tenant, segment) ->
                                           subscribeRegistrations.putIfAbsent(tenant, segment.subscribe(commandName, handler)));
            return handler;
        });
        return () -> subscribeRegistrations.values().stream().map(Registration::cancel).reduce((prev, acc) -> prev && acc).orElse(false);
    }


    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        Map<TenantDescriptor, List<Registration>> newRegistrations = new HashMap<>();
        tenantSegments.forEach((tenant, bus) ->
                                       newRegistrations
                                               .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                               .add(bus.registerDispatchInterceptor(dispatchInterceptor)));

        dispatchInterceptorsRegistration.putAll(newRegistrations);

        return () -> newRegistrations.values().stream()
                                     .flatMap(Collection::stream)
                                     .filter(Objects::nonNull)
                                     .map(Registration::cancel)
                                     .reduce((prev, acc) -> prev && acc)
                                     .orElse(false);
    }
    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        handlerInterceptors.add(handlerInterceptor);
        Map<TenantDescriptor, List<Registration>> newRegistrations = new HashMap<>();
        tenantSegments.forEach((tenant, bus) ->
                                       newRegistrations
                                               .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                               .add(bus.registerHandlerInterceptor(handlerInterceptor)));

        handlerInterceptorsRegistration.putAll(newRegistrations);

        return () -> newRegistrations.values()
                                     .stream()
                                     .flatMap(Collection::stream)
                                     .map(Registration::cancel)
                                     .reduce((prev, acc) -> prev && acc)
                                     .orElse(false);
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

            dispatchInterceptors.forEach(handlerInterceptor ->
                                                 dispatchInterceptorsRegistration
                                                         .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                         .add(tenantSegment.registerDispatchInterceptor(
                                                                 handlerInterceptor)));

            handlerInterceptors.forEach(handlerInterceptor ->
                                                handlerInterceptorsRegistration
                                                        .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                        .add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

            handlers.forEach((commandName, handler) ->
                                     subscribeRegistrations.putIfAbsent(tenantDescriptor, tenantSegment.subscribe(commandName, handler)));

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

    public static class Builder {

        public TenantCommandSegmentFactory tenantSegmentFactory;
        public TargetTenantResolver<CommandMessage<?>> targetTenantResolver;

        /**
         * Sets the {@link TenantCommandSegmentFactory} used to build {@link CommandBus} segment for given {@link
         * TenantDescriptor}.
         *
         * @param tenantSegmentFactory tenant aware segment factory
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tenantSegmentFactory(TenantCommandSegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * Sets the {@link TargetTenantResolver} used to resolve correct tenant segment based on {@link Message}
         * message
         *
         * @param targetTenantResolver used to resolve correct tenant segment based on {@link Message} message
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder targetTenantResolver(TargetTenantResolver<CommandMessage<?>> targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        public MultiTenantCommandBus build() {
            return new MultiTenantCommandBus(this);
        }

        protected void validate() {
            assertNonNull(targetTenantResolver, "The TargetTenantResolver is a hard requirement");
            assertNonNull(tenantSegmentFactory, "The TenantEventProcessorSegmentFactory is a hard requirement");
        }
    }
}
