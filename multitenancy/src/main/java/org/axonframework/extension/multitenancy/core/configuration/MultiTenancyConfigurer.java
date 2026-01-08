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
package org.axonframework.extension.multitenancy.core.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.extension.multitenancy.core.TenantConnectPredicate;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.eventsourcing.eventstore.TenantEventSegmentFactory;
import org.axonframework.extension.multitenancy.messaging.commandhandling.TenantCommandSegmentFactory;
import org.axonframework.extension.multitenancy.core.TenantComponentFactory;
import org.axonframework.extension.multitenancy.core.TenantComponentRegistry;
import org.axonframework.extension.multitenancy.messaging.core.unitofwork.annotation.TenantAwareProcessingContextResolverFactory;
import org.axonframework.extension.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.extension.multitenancy.messaging.eventhandling.processing.TenantEventProcessorSegmentFactory;
import org.axonframework.extension.multitenancy.messaging.queryhandling.TenantQuerySegmentFactory;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.core.configuration.reflection.ParameterResolverFactoryUtils;
import org.axonframework.messaging.queryhandling.QueryBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * The multitenancy {@link ApplicationConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for multi-tenant infrastructure components including
 * {@link #registerTenantProvider(ComponentBuilder) tenant provider},
 * {@link #registerTargetTenantResolver(ComponentBuilder) tenant resolver},
 * and tenant segment factories for commands, queries, events, and event processors.
 * <p>
 * This configurer enhances a {@link MessagingConfigurer} by replacing standard infrastructure
 * components with their multi-tenant equivalents.
 * <p>
 * Example usage:
 * <pre><code>
 *     MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
 *                           .registerTenantProvider(config -> myTenantProvider)
 *                           .registerTargetTenantResolver(config -> new MetadataBasedTenantResolver())
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
    private final List<TenantComponentRegistration<?>> tenantComponentRegistrations = new ArrayList<>();
    private boolean resolverFactoryRegistered = false;

    /**
     * Holds a tenant component registration: the type and its factory.
     */
    private record TenantComponentRegistration<T>(Class<T> componentType, TenantComponentFactory<T> factory) {
    }

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
        MultiTenancyConfigurer configurer = new MultiTenancyConfigurer(applicationConfigurer);
        configurer.componentRegistry(cr -> cr.registerEnhancer(new MultiTenancyConfigurationDefaults()));
        // Register resolver factory early so modules built later can access tenant components.
        // The factory creation lambda captures tenantComponentRegistrations by reference,
        // so it will see all registrations made before build() is called.
        configurer.ensureResolverFactoryRegistered();
        return configurer;
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

    /**
     * Registers a tenant-scoped component that will be created per-tenant and injected
     * into event handlers, query handlers, and other message handlers.
     * <p>
     * The factory receives a {@link org.axonframework.extension.multitenancy.core.TenantDescriptor}
     * and should return a tenant-specific instance of the component. Components are created lazily
     * on first access and cached per tenant.
     * <p>
     * Example usage:
     * <pre>{@code
     * MultiTenancyConfigurer.enhance(configurer)
     *     .tenantComponent(OrderRepository.class, tenant -> new InMemoryOrderRepository())
     *     .tenantComponent(MetricsService.class, tenant -> new TenantMetrics(tenant.tenantId()));
     * }</pre>
     * <p>
     * Handlers can then receive the tenant-scoped component via parameter injection:
     * <pre>{@code
     * @EventHandler
     * void on(OrderCreatedEvent event, OrderRepository repository) {
     *     repository.save(new OrderProjection(event));
     * }
     * }</pre>
     *
     * @param componentType The class of the component to register
     * @param factory       The factory that creates tenant-specific instances
     * @param <T>           The component type
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public <T> MultiTenancyConfigurer tenantComponent(
            @Nonnull Class<T> componentType,
            @Nonnull TenantComponentFactory<T> factory
    ) {
        requireNonNull(componentType, "The component type must not be null.");
        requireNonNull(factory, "The component factory must not be null.");
        tenantComponentRegistrations.add(new TenantComponentRegistration<>(componentType, factory));
        ensureResolverFactoryRegistered();
        return this;
    }

    /**
     * Registers a tenant-scoped component with a custom cleanup callback.
     * <p>
     * This overload allows specifying explicit cleanup logic for components that need
     * special handling when a tenant is removed. For components that implement
     * {@link AutoCloseable}, consider using {@link #tenantComponent(Class, TenantComponentFactory)}
     * which handles cleanup automatically.
     * <p>
     * Example usage:
     * <pre>{@code
     * MultiTenancyConfigurer.enhance(configurer)
     *     .tenantComponent(
     *         ConnectionPool.class,
     *         tenant -> createPoolForTenant(tenant),
     *         (tenant, pool) -> {
     *             pool.drain();
     *             pool.close();
     *             logger.info("Closed pool for tenant {}", tenant.tenantId());
     *         }
     *     );
     * }</pre>
     *
     * @param componentType The class of the component to register
     * @param factory       The factory that creates tenant-specific instances
     * @param cleanup       The cleanup callback invoked when a tenant is removed
     * @param <T>           The component type
     * @return The current instance of the {@code MultiTenancyConfigurer} for a fluent API.
     */
    public <T> MultiTenancyConfigurer tenantComponent(
            @Nonnull Class<T> componentType,
            @Nonnull Function<TenantDescriptor, T> factory,
            @Nonnull BiConsumer<TenantDescriptor, T> cleanup
    ) {
        requireNonNull(componentType, "The component type must not be null.");
        requireNonNull(factory, "The component factory must not be null.");
        requireNonNull(cleanup, "The cleanup callback must not be null.");

        // Wrap in TenantComponentFactory with custom cleanup
        TenantComponentFactory<T> wrappedFactory = new TenantComponentFactory<>() {
            @Override
            public T apply(TenantDescriptor tenant) {
                return factory.apply(tenant);
            }

            @Override
            public void cleanup(TenantDescriptor tenant, T component) {
                cleanup.accept(tenant, component);
            }
        };

        tenantComponentRegistrations.add(new TenantComponentRegistration<>(componentType, wrappedFactory));
        ensureResolverFactoryRegistered();
        return this;
    }

    /**
     * Registers the parameter resolver factories for tenant-scoped components.
     * This is called automatically when the first tenant component is registered.
     * <p>
     * Registers two parameter resolver factories:
     * <ol>
     *   <li>{@link TenantComponentResolverFactory} - Resolves tenant-scoped component parameters</li>
     *   <li>{@link TenantAwareProcessingContextResolverFactory} - Wraps {@code ProcessingContext}
     *       parameters with tenant-aware functionality for {@code context.component()} access</li>
     * </ol>
     * <p>
     * Uses {@link ParameterResolverFactoryUtils} to properly integrate with existing
     * parameter resolver factories via {@link org.axonframework.messaging.core.annotation.MultiParameterResolverFactory}.
     */
    private void ensureResolverFactoryRegistered() {
        if (resolverFactoryRegistered) {
            return;
        }
        resolverFactoryRegistered = true;

        // Register a single ParameterResolverFactory that creates both factories
        // This avoids circular dependency issues when resolving components
        delegate.componentRegistry(cr -> ParameterResolverFactoryUtils.registerToComponentRegistry(
                cr,
                config -> {
                    // Get the tenant resolver for both factories
                    TargetTenantResolver<Message> tenantResolver = config.getComponent(TargetTenantResolver.class);

                    // Create the TenantComponentResolverFactory
                    TenantComponentResolverFactory componentFactory = new TenantComponentResolverFactory(tenantResolver);

                    // Register all tenant components in the factory
                    for (TenantComponentRegistration<?> registration : tenantComponentRegistrations) {
                        registerComponentInFactory(componentFactory, registration, config);
                    }

                    // Create the TenantAwareProcessingContextResolverFactory
                    TenantAwareProcessingContextResolverFactory contextFactory =
                            new TenantAwareProcessingContextResolverFactory(componentFactory, tenantResolver);

                    // Return a MultiParameterResolverFactory containing both
                    return MultiParameterResolverFactory.ordered(componentFactory, contextFactory);
                }
        ));
    }

    /**
     * Helper method to register a component in the resolver factory with proper typing.
     */
    @SuppressWarnings("unchecked")
    private <T> void registerComponentInFactory(
            TenantComponentResolverFactory resolverFactory,
            TenantComponentRegistration<T> registration,
            org.axonframework.common.configuration.Configuration config
    ) {
        TenantComponentRegistry<T> registry = resolverFactory.registerComponent(
                registration.componentType(),
                registration.factory()
        );

        // Subscribe registry to tenant provider for lifecycle management
        TenantProvider tenantProvider = config.getComponent(TenantProvider.class);
        if (tenantProvider != null) {
            tenantProvider.subscribe(registry);
            tenantProvider.getTenants().forEach(registry::registerTenant);
        }
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
