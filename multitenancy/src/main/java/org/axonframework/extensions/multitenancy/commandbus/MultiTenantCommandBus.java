package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/*
@author Allard Buijze
@author Steven van Beelen
@author Stefan Dragisic
 */

public class MultiTenantCommandBus implements CommandBus, MultiTenantBus {

    private final Map<TenantDescriptor, CommandBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<String, MessageHandler<? super CommandMessage<?>>> commandHandlers = new ConcurrentHashMap<>();

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
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(command,
                Collections.unmodifiableCollection(tenantSegments.keySet()));
        CommandBus tenantCommandBus = tenantSegments.get(tenantDescriptor);
        if (tenantCommandBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        tenantCommandBus.dispatch(command);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(command,
                Collections.unmodifiableCollection(tenantSegments.keySet()));
        CommandBus tenantCommandBus = tenantSegments.get(tenantDescriptor);
        if (tenantCommandBus == null) {
            NoSuchTenantException dispatchException = new NoSuchTenantException(tenantDescriptor.tenantId());
            callback.onResult(
                    command, GenericCommandResultMessage.asCommandResultMessage(dispatchException)
            );
        } else {
            tenantCommandBus.dispatch(command, callback);
        }
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        commandHandlers.computeIfAbsent(commandName, k -> {
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

    //who ever call this method needs to records all registrations and keep list of them
    //factory method needs call AxonServerConnectorModule and create segment from teenat descriptor
    //tennant descriptor is created from list all context api
    //call registration cancel of a specific teenant to stop listing his updates

    //todo move to builder
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

    //to be used by user or independently durring runtime
    @Override
    public void registerAndSubscribeTenant(TenantDescriptor tenantDescriptor) {
        tenantSegments.computeIfAbsent(tenantDescriptor, k -> {
            CommandBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);

            dispatchInterceptors.forEach(dispatchInterceptor ->
                    dispatchInterceptorsRegistration.add(tenantSegment.registerDispatchInterceptor(dispatchInterceptor)));

            handlerInterceptors.forEach(handlerInterceptor ->
                    handlerInterceptorsRegistration.add(tenantSegment.registerHandlerInterceptor(handlerInterceptor)));

            commandHandlers.forEach((commandName, handler) ->
                    subscribeRegistrations.putIfAbsent(tenantDescriptor, tenantSegment.subscribe(commandName, handler)));

            return tenantSegment;
        });
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
            //assert targetTenantResolver set
            //assert tenantSegmentFactory set

        }
    }
}
