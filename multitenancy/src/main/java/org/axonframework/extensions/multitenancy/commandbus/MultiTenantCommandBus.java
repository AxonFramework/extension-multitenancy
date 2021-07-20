package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MultiTenantCommandBus implements CommandBus {

    private final Map<String, CommandBus> tenantSegments = new ConcurrentHashMap<>();
    private final Map<String, MessageHandler<? super CommandMessage<?>>> handlers = new ConcurrentHashMap<>();

    private final Function<TenantDescriptor, CommandBus> tenantSegmentFactory;
    private final Function<? super CommandMessage<?>, String> targetTenantResolver;

    public MultiTenantCommandBus(Function<TenantDescriptor, CommandBus> tenantSegmentFactory,
                                 Function<? super CommandMessage<?>, String> targetTenantResolver) {
        this.tenantSegmentFactory = tenantSegmentFactory;
        this.targetTenantResolver = targetTenantResolver;
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        String targetTenantId = targetTenantResolver.apply(command);
        CommandBus tenantCommandBus = tenantSegments.get(targetTenantId);
        if (tenantCommandBus == null) {
            throw new NoSuchTenantException(targetTenantId);
        }
        tenantCommandBus.dispatch(command);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
        String targetTenantId = targetTenantResolver.apply(command);
        CommandBus tenantCommandBus = tenantSegments.get(targetTenantId);
        if (tenantCommandBus == null) {
            NoSuchTenantException dispatchException = new NoSuchTenantException(targetTenantId);
            callback.onResult(
                    command, GenericCommandResultMessage.asCommandResultMessage(dispatchException)
            );
        } else {
            tenantCommandBus.dispatch(command, callback);
        }
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        // iterate over tenant segments
        // apply filter on each one
        return null;
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return null;
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return null;
    }

    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        return () -> {
            CommandBus delegate = tenantSegments.remove(tenantDescriptor.tenantId());
            return delegate != null;
        };
    }
}
