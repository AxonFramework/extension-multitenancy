package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.MultiTenantAwareComponent;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/*
@author Allard Buijze
@author Steven van Beelen
@author Stefan Dragisic
 */

public class MultiTenantCommandBus implements CommandBus, MultiTenantAwareComponent {

    private final Map<TenantDescriptor, CommandBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<String, MessageHandler<? super CommandMessage<?>>> handlers = new ConcurrentHashMap<>();

    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final List<Registration> dispatchInterceptorsRegistration = new CopyOnWriteArrayList<>();

    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final List<Registration> handlerInterceptorsRegistration = new CopyOnWriteArrayList<>();

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
        tenantSegments.forEach((tenant, bus) ->
                dispatchInterceptorsRegistration.add(bus.registerDispatchInterceptor(dispatchInterceptor)));

        return () -> dispatchInterceptorsRegistration.stream().map(Registration::cancel).reduce((prev, acc) -> prev && acc).orElse(false);
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        handlerInterceptors.add(handlerInterceptor);
        tenantSegments.forEach((tenant, bus) ->
                handlerInterceptorsRegistration.add(bus.registerHandlerInterceptor(handlerInterceptor)));

        return () -> handlerInterceptorsRegistration.stream().map(Registration::cancel).reduce((prev, acc) -> prev && acc).orElse(false);
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
        subscribeRegistrations.remove(tenantDescriptor).cancel();
        return tenantSegments.remove(tenantDescriptor);
    }

    @Override
    public Registration registerTenantAndSubscribe(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, k -> {
            CommandBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            dispatchInterceptors.forEach(dispatchInterceptor ->
                    dispatchInterceptorsRegistration.add(tenantSegment.registerDispatchInterceptor(dispatchInterceptor)));

            handlerInterceptors.forEach(handlerInterceptor ->
                    handlerInterceptorsRegistration.add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

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
         * @param tenantSegmentFactory
         * @return
         */
        public Builder tenantSegmentFactory(TenantCommandSegmentFactory tenantSegmentFactory) {
            BuilderUtils.assertNonNull(tenantSegmentFactory, "");
            this.tenantSegmentFactory = tenantSegmentFactory;
            return this;
        }

        /**
         * @param targetTenantResolver
         * @return
         */
        public Builder targetTenantResolver(TargetTenantResolver targetTenantResolver) {
            BuilderUtils.assertNonNull(targetTenantResolver, "");
            this.targetTenantResolver = targetTenantResolver;
            return this;
        }

        public MultiTenantCommandBus build() {
            return new MultiTenantCommandBus(this);
        }

        protected void validate() {
            // todo
        }
    }
}
