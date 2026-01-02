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
package org.axonframework.extensions.multitenancy.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.eventsourcing.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.commandhandling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.queryhandling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.eventhandling.processing.TenantEventProcessorSegmentFactory;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.queryhandling.QueryBus;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * The multitenancy {@link ApplicationConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for multi-tenant infrastructure components including
 * {@link #registerTenantProvider(ComponentBuilder) tenant provider},
 * {@link #registerTargetTenantResolver(ComponentBuilder) tenant resolver}, and
 * tenant segment factories for commands, queries, events, and event processors.
 * <p>
 * This configurer enhances a {@link MessagingConfigurer} by replacing standard infrastructure
 * components with their multi-tenant equivalents.
 * <p>
 * Example usage:
 * <pre><code>
 *     MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
 *                           .registerTenantProvider(config -> myTenantProvider)
 *                           .registerTargetTenantResolver(config -> myResolver)
 *                           .registerCommandBusSegmentFactory(config -> tenant -> createBusForTenant(tenant))
 *                           .build()
 *                           .start();
 * </code></pre>
 *
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class MultiTenancyConfigurer implements ApplicationConfigurer {

    private final ApplicationConfigurer delegate;

    /**
     * Constructs a {@code MultiTenancyConfigurer} based on the given {@code delegate}.
     *
     * @param delegate The delegate {@code ApplicationConfigurer} the {@code MultiTenancyConfigurer} is based on.
     */
    private MultiTenancyConfigurer(@Nonnull ApplicationConfigurer delegate) {
        this.delegate = requireNonNull(delegate, "The Application Configurer cannot be null.");
    }

    /**
     * Creates a MultiTenancyConfigurer that enhances an existing {@code ApplicationConfigurer}.
     * This method is useful when applying multiple specialized Configurers to configure a single application.
     *
     * @param applicationConfigurer The {@code ApplicationConfigurer} to enhance with multi-tenancy configuration.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public static MultiTenancyConfigurer enhance(@Nonnull ApplicationConfigurer applicationConfigurer) {
        return new MultiTenancyConfigurer(applicationConfigurer)
                .componentRegistry(cr -> cr.registerEnhancer(new MultiTenancyConfigurationDefaults()));
    }

    /**
     * Creates a MultiTenancyConfigurer that enhances a {@code MessagingConfigurer}.
     * This is the typical entry point for multi-tenant applications.
     *
     * @param messagingConfigurer The {@code MessagingConfigurer} to enhance with multi-tenancy configuration.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public static MultiTenancyConfigurer enhance(@Nonnull MessagingConfigurer messagingConfigurer) {
        return enhance((ApplicationConfigurer) messagingConfigurer);
    }

    /**
     * Registers the given {@link TenantProvider} factory in this {@code Configurer}.
     * <p>
     * The {@code tenantProviderBuilder} receives the configuration as input and is expected to return a
     * {@link TenantProvider} instance that manages the available tenants.
     *
     * @param tenantProviderBuilder The builder constructing the {@link TenantProvider}.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public MultiTenancyConfigurer registerTenantProvider(
            @Nonnull ComponentBuilder<TenantProvider> tenantProviderBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(TenantProvider.class, tenantProviderBuilder));
        return this;
    }

    /**
     * Registers the given {@link TargetTenantResolver} factory in this {@code Configurer}.
     * <p>
     * The resolver is used to determine which tenant a message should be routed to based on
     * the message's metadata or payload.
     *
     * @param resolverBuilder The builder constructing the {@link TargetTenantResolver}.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    @SuppressWarnings("unchecked")
    public MultiTenancyConfigurer registerTargetTenantResolver(
            @Nonnull ComponentBuilder<TargetTenantResolver<Message>> resolverBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(
                (Class<TargetTenantResolver<Message>>) (Class<?>) TargetTenantResolver.class,
                resolverBuilder
        ));
        return this;
    }

    /**
     * Registers the given {@link TenantConnectPredicate} factory in this {@code Configurer}.
     * <p>
     * The predicate is used to filter which tenants should be connected to dynamically.
     *
     * @param predicateBuilder The builder constructing the {@link TenantConnectPredicate}.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public MultiTenancyConfigurer registerTenantConnectPredicate(
            @Nonnull ComponentBuilder<TenantConnectPredicate> predicateBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(TenantConnectPredicate.class, predicateBuilder));
        return this;
    }

    /**
     * Registers the given {@link TenantCommandSegmentFactory} factory in this {@code Configurer}.
     * <p>
     * The factory creates {@link CommandBus} instances for each tenant.
     *
     * @param factoryBuilder The builder constructing the {@link TenantCommandSegmentFactory}.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public MultiTenancyConfigurer registerCommandBusSegmentFactory(
            @Nonnull ComponentBuilder<TenantCommandSegmentFactory> factoryBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(TenantCommandSegmentFactory.class, factoryBuilder));
        return this;
    }

    /**
     * Registers the given {@link TenantQuerySegmentFactory} factory in this {@code Configurer}.
     * <p>
     * The factory creates {@link QueryBus} instances for each tenant.
     *
     * @param factoryBuilder The builder constructing the {@link TenantQuerySegmentFactory}.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public MultiTenancyConfigurer registerQueryBusSegmentFactory(
            @Nonnull ComponentBuilder<TenantQuerySegmentFactory> factoryBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(TenantQuerySegmentFactory.class, factoryBuilder));
        return this;
    }

    /**
     * Registers the given {@link TenantEventSegmentFactory} factory in this {@code Configurer}.
     * <p>
     * The factory creates event store instances for each tenant.
     *
     * @param factoryBuilder The builder constructing the {@link TenantEventSegmentFactory}.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public MultiTenancyConfigurer registerEventStoreSegmentFactory(
            @Nonnull ComponentBuilder<TenantEventSegmentFactory> factoryBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(TenantEventSegmentFactory.class, factoryBuilder));
        return this;
    }

    /**
     * Registers the given {@link TenantEventProcessorSegmentFactory} factory in this {@code Configurer}.
     * <p>
     * The factory creates event processor instances for each tenant.
     *
     * @param factoryBuilder The builder constructing the {@link TenantEventProcessorSegmentFactory}.
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public MultiTenancyConfigurer registerEventProcessorSegmentFactory(
            @Nonnull ComponentBuilder<TenantEventProcessorSegmentFactory> factoryBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(
                TenantEventProcessorSegmentFactory.class, factoryBuilder
        ));
        return this;
    }

    @Override
    public MultiTenancyConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(
                requireNonNull(componentRegistrar, "The component registrar must not be null.")
        );
        return this;
    }

    @Override
    public MultiTenancyConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        delegate.lifecycleRegistry(
                requireNonNull(lifecycleRegistrar, "The lifecycle registrar must not be null.")
        );
        return this;
    }

    @Override
    public AxonConfiguration build() {
        return delegate.build();
    }
}
