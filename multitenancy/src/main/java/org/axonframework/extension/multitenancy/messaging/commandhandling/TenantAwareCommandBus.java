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
package org.axonframework.extension.multitenancy.messaging.commandhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link CommandBus} wrapper that ensures the command message is added to the
 * {@link ProcessingContext} before handler invocation.
 * <p>
 * This is essential for multi-tenant scenarios where downstream components (like
 * {@link org.axonframework.extension.multitenancy.eventsourcing.eventstore.MultiTenantEventStore})
 * need to resolve the tenant from the message in the processing context.
 * <p>
 * The wrapper intercepts handler subscriptions and wraps each handler to add the
 * command message to the context before delegating to the actual handler.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public class TenantAwareCommandBus implements CommandBus {

    private final CommandBus delegate;

    /**
     * Creates a new {@code TenantAwareCommandBus} wrapping the given delegate.
     *
     * @param delegate The {@link CommandBus} to wrap.
     */
    public TenantAwareCommandBus(@Nonnull CommandBus delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        return delegate.dispatch(command, processingContext);
    }

    @Override
    public CommandBus subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler) {
        // Wrap the handler to add the command message to context before handling
        CommandHandler wrappedHandler = (command, context) -> {
            ProcessingContext contextWithMessage = Message.addToContext(context, command);
            return commandHandler.handle(command, contextWithMessage);
        };
        delegate.subscribe(name, wrappedHandler);
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("delegate", delegate);
    }
}
