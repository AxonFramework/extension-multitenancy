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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/*
@author Allard Buijze
@author Steven van Beelen
@author Stefan Dragisic
 */

public class MultiTenantCommandBus implements CommandBus {

    private final Map<TenantDescriptor, CommandBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, MessageHandler<? super CommandMessage<?>>> handlers = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Registration>> tenantRegistrations = new ConcurrentHashMap<>();

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
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(command, tenantSegments.keySet());
        CommandBus tenantCommandBus = tenantSegments.get(tenantDescriptor);
        if (tenantCommandBus == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        tenantCommandBus.dispatch(command);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
        TenantDescriptor tenantDescriptor = targetTenantResolver.resolveTenant(command, tenantSegments.keySet());
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
        Map<String, Registration> registrationMap = tenantSegments.entrySet()
                .stream()
                .collect(Collectors.toMap(key -> key.getKey().tenantId(), entry -> entry.getValue().subscribe(commandName, handler)));

        //todo add too tenantRegistrations

        return () -> {
            //todo iterate registrationMap and cancel all
            return true;
        };
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return null;
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return null;
    }

    //who ever call this method needs to records all registrations and keep list of them
    //factory method needs call AxonServerConnectorModule and create segment from teenat descriptor
    //tennant descriptor is created from list all context api
    //call registration cancel of a specific teenant to stop listing his updates
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {

        CommandBus tenantSegment = tenantSegmentFactory.apply(tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            CommandBus delegate = unregisterTenant(tenantDescriptor);
            return delegate != null;
        };
    }

    public CommandBus unregisterTenant(TenantDescriptor tenantDescriptor) {
        return tenantSegments.remove(tenantDescriptor);
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
