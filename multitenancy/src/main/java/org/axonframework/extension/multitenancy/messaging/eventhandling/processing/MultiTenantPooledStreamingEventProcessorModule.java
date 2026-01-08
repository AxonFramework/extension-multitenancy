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
package org.axonframework.extension.multitenancy.messaging.eventhandling.processing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.configuration.BaseModule;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ModuleBuilder;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantComponentFactory;
import org.axonframework.extension.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.eventsourcing.eventstore.TenantEventStoreProvider;
import org.axonframework.messaging.core.Message;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.DefaultEventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorCustomization;
import org.axonframework.messaging.eventhandling.interception.InterceptingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SequenceCachingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * A configuration module for creating a {@link MultiTenantEventProcessor} that wraps
 * per-tenant {@link PooledStreamingEventProcessor} instances.
 * <p>
 * This module follows the same pattern as {@link org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule}
 * but creates a multi-tenant aware processor that:
 * <ul>
 *     <li>Creates separate {@link PooledStreamingEventProcessor} instances for each tenant</li>
 *     <li>Routes to tenant-specific event stores via {@link MultiTenantEventStore}</li>
 *     <li>Uses tenant-specific token stores via {@link TenantTokenStoreFactory}</li>
 *     <li>Dynamically adds/removes tenant processors as tenants are registered/unregistered</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * configurer.messaging(m -> m
 *     .eventProcessing(ep -> ep
 *         .pooledStreaming(ps -> ps
 *             .processor(
 *                 MultiTenantPooledStreamingEventProcessorModule.create("orderProjection")
 *                     .eventHandlingComponents(c -> c.autodetected(cfg -> new OrderProjection()))
 *                     .customized((cfg, config) -> config.batchSize(100))
 *             )
 *         )
 *     )
 * );
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see MultiTenantEventProcessor
 * @see TenantTokenStoreFactory
 */
public class MultiTenantPooledStreamingEventProcessorModule extends BaseModule<MultiTenantPooledStreamingEventProcessorModule>
        implements EventProcessorModule, ModuleBuilder<MultiTenantPooledStreamingEventProcessorModule>,
        EventProcessorModule.EventHandlingPhase<MultiTenantPooledStreamingEventProcessorModule, MultiTenantPooledStreamingEventProcessorConfiguration>,
        EventProcessorModule.CustomizationPhase<MultiTenantPooledStreamingEventProcessorModule, MultiTenantPooledStreamingEventProcessorConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantPooledStreamingEventProcessorModule.class);

    private final String processorName;
    private List<ComponentBuilder<EventHandlingComponent>> eventHandlingComponentBuilders;
    private BiFunction<Configuration, MultiTenantPooledStreamingEventProcessorConfiguration, MultiTenantPooledStreamingEventProcessorConfiguration> instanceCustomization;
    private final List<TenantComponentRegistration<?>> tenantComponentRegistrations = new ArrayList<>();

    /**
     * Holds a tenant component registration: the type and its factory.
     */
    private record TenantComponentRegistration<T>(Class<T> componentType, TenantComponentFactory<T> factory) {
    }

    /**
     * Creates a new multi-tenant pooled streaming event processor module with the given name.
     *
     * @param processorName The name of the processor.
     * @return A new module instance in the event handling configuration phase.
     */
    public static EventHandlingPhase<MultiTenantPooledStreamingEventProcessorModule, MultiTenantPooledStreamingEventProcessorConfiguration> create(
            @Nonnull String processorName
    ) {
        return new MultiTenantPooledStreamingEventProcessorModule(processorName);
    }

    /**
     * Constructs a module with the given processor name.
     *
     * @param processorName The unique name for the multi-tenant event processor.
     */
    public MultiTenantPooledStreamingEventProcessorModule(@Nonnull String processorName) {
        super(processorName);
        this.processorName = Objects.requireNonNull(processorName, "Processor name must not be null");
        this.instanceCustomization = (cfg, config) -> config;
    }

    @Override
    public CustomizationPhase<MultiTenantPooledStreamingEventProcessorModule, MultiTenantPooledStreamingEventProcessorConfiguration> eventHandlingComponents(
            @Nonnull Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> configurerTask
    ) {
        var configurer = new DefaultEventHandlingComponentsConfigurer();
        this.eventHandlingComponentBuilders = configurerTask.apply(configurer).toList();
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorModule customized(
            @Nonnull BiFunction<Configuration, MultiTenantPooledStreamingEventProcessorConfiguration, MultiTenantPooledStreamingEventProcessorConfiguration> instanceCustomization
    ) {
        this.instanceCustomization = Objects.requireNonNull(instanceCustomization);
        return this;
    }

    /**
     * Registers a tenant-scoped component that will be resolved per-tenant in event handlers.
     * <p>
     * When an event handler has a parameter of the specified component type, the framework will
     * automatically inject the tenant-scoped instance based on the event's tenant context.
     * <p>
     * Multiple tenant components can be registered on the same processor:
     * <pre>{@code
     * MultiTenantPooledStreamingEventProcessorModule
     *     .create("OrderProjection")
     *     .eventHandlingComponents(c -> c.autodetected(cfg -> new OrderProjector()))
     *     .tenantComponent(OrderRepository.class, tenant -> new InMemoryOrderRepository())
     *     .tenantComponent(MetricsService.class, tenant -> new TenantMetrics(tenant.tenantId()))
     *     .build();
     * }</pre>
     * <p>
     * The handler can then receive these components as method parameters:
     * <pre>{@code
     * @EventHandler
     * void handle(OrderCreated event, OrderRepository repo, MetricsService metrics) {
     *     repo.save(new OrderProjection(event));
     *     metrics.recordEvent("order.created");
     * }
     * }</pre>
     *
     * @param componentType The type of component to register
     * @param factory       Factory that creates component instances per tenant
     * @param <T>           The component type
     * @return This module for fluent configuration
     */
    public <T> MultiTenantPooledStreamingEventProcessorModule tenantComponent(
            @Nonnull Class<T> componentType,
            @Nonnull TenantComponentFactory<T> factory
    ) {
        Objects.requireNonNull(componentType, "Component type must not be null");
        Objects.requireNonNull(factory, "Factory must not be null");
        tenantComponentRegistrations.add(new TenantComponentRegistration<>(componentType, factory));
        return this;
    }

    @Override
    public MultiTenantPooledStreamingEventProcessorModule build() {
        logger.debug("Building MultiTenantPooledStreamingEventProcessorModule [{}]", processorName);
        registerTenantComponentResolverFactory();
        registerEventHandlingComponents();
        registerMultiTenantEventProcessor();
        return this;
    }

    @SuppressWarnings("unchecked")
    private void registerTenantComponentResolverFactory() {
        if (tenantComponentRegistrations.isEmpty()) {
            return;
        }

        // Register as ParameterResolverFactory so it's picked up by parameter resolution
        String resolverFactoryName = "TenantComponentResolverFactory[" + processorName + "]";
        componentRegistry(cr -> cr.registerComponent(
                ParameterResolverFactory.class,
                resolverFactoryName,
                config -> {
                    TargetTenantResolver<Message> tenantResolver = config.getComponent(TargetTenantResolver.class);
                    TenantComponentResolverFactory factory = new TenantComponentResolverFactory(tenantResolver);

                    // Register all tenant components
                    for (TenantComponentRegistration<?> registration : tenantComponentRegistrations) {
                        registerTenantComponent(factory, registration);
                    }

                    // Subscribe registries to tenant provider for lifecycle management
                    TenantProvider tenantProvider = config.getComponent(TenantProvider.class);
                    if (tenantProvider != null) {
                        factory.getRegistries().values().forEach(registry -> {
                            tenantProvider.subscribe(registry);
                            tenantProvider.getTenants().forEach(registry::registerTenant);
                        });
                    }

                    return factory;
                }
        ));
    }

    @SuppressWarnings("unchecked")
    private <T> void registerTenantComponent(TenantComponentResolverFactory factory,
                                             TenantComponentRegistration<T> registration) {
        factory.registerComponent(registration.componentType(), registration.factory());
    }

    private void registerEventHandlingComponents() {
        for (int i = 0; i < eventHandlingComponentBuilders.size(); i++) {
            var componentBuilder = eventHandlingComponentBuilders.get(i);
            var componentName = processorEventHandlingComponentName(i);
            componentRegistry(cr -> {
                cr.registerComponent(EventHandlingComponent.class, componentName,
                                     cfg -> {
                                         var component = componentBuilder.build(cfg);
                                         return new SequenceCachingEventHandlingComponent(component);
                                     });
                cr.registerDecorator(EventHandlingComponent.class, componentName,
                                     InterceptingEventHandlingComponent.DECORATION_ORDER,
                                     (config, name, delegate) -> {
                                         var processorConfig = getBaseConfiguration(config);
                                         return new InterceptingEventHandlingComponent(
                                                 processorConfig.interceptors(),
                                                 delegate
                                         );
                                     });
            });
        }
    }

    private void registerMultiTenantEventProcessor() {
        var processorComponentDefinition = ComponentDefinition
                .ofTypeAndName(EventProcessor.class, processorName)
                .withBuilder(this::buildMultiTenantEventProcessor)
                .onStart(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    return component.start();
                })
                .onShutdown(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    return component.shutdown();
                });

        componentRegistry(cr -> cr.registerComponent(processorComponentDefinition));
    }

    private MultiTenantEventProcessor buildMultiTenantEventProcessor(Configuration config) {
        logger.debug("Creating MultiTenantEventProcessor [{}]", processorName);

        // Get required components
        TenantEventStoreProvider tenantEventStoreProvider = config.getComponent(TenantEventStoreProvider.class);
        TenantProvider tenantProvider = config.getComponent(TenantProvider.class);

        // Get the event handling components (shared across all tenants)
        List<EventHandlingComponent> eventHandlingComponents = getEventHandlingComponents(config);

        // Get base configuration for non-tenant-specific settings
        MultiTenantPooledStreamingEventProcessorConfiguration baseConfig = getBaseConfiguration(config);

        // Use per-processor token store factory if configured, otherwise fall back to global
        TenantTokenStoreFactory tokenStoreFactory = baseConfig.tenantTokenStoreFactory() != null
                ? baseConfig.tenantTokenStoreFactory()
                : config.getComponent(TenantTokenStoreFactory.class, InMemoryTenantTokenStoreFactory::new);

        // Create the tenant segment factory
        TenantEventProcessorSegmentFactory segmentFactory = tenant -> {
            logger.debug("Creating PooledStreamingEventProcessor for tenant [{}]", tenant.tenantId());

            // Get tenant-specific components
            EventStore tenantEventStore = tenantEventStoreProvider.tenantSegments().get(tenant);
            if (tenantEventStore == null) {
                throw new IllegalStateException(
                        "No event store segment found for tenant [" + tenant.tenantId() + "]. " +
                        "Ensure the tenant is registered with the MultiTenantEventStore."
                );
            }
            TokenStore tenantTokenStore = tokenStoreFactory.apply(tenant);

            // Create tenant-specific configuration with dedicated executors
            // Must pass config to get proper ApplicationContext for sequencing policy
            String tenantProcessorName = processorName + "@" + tenant.tenantId();
            MultiTenantPooledStreamingEventProcessorConfiguration tenantConfig = new MultiTenantPooledStreamingEventProcessorConfiguration(baseConfig, config)
                    .eventSource(tenantEventStore)
                    .tokenStore(tenantTokenStore)
                    .coordinatorExecutor(createExecutor("Coordinator[" + tenantProcessorName + "]"))
                    .workerExecutor(createExecutor("WorkPackage[" + tenantProcessorName + "]"));

            // Create the per-tenant processor
            return new PooledStreamingEventProcessor(
                    tenantProcessorName,
                    eventHandlingComponents,
                    tenantConfig
            );
        };

        // Build the multi-tenant processor
        MultiTenantEventProcessor multiTenantProcessor = MultiTenantEventProcessor.builder()
                .name(processorName)
                .tenantSegmentFactory(segmentFactory)
                .build();

        // Subscribe to tenant provider for dynamic tenant management
        if (tenantProvider != null) {
            tenantProvider.subscribe(multiTenantProcessor);
            // Register existing tenants
            tenantProvider.getTenants().forEach(multiTenantProcessor::registerTenant);
        }

        logger.debug("Created MultiTenantEventProcessor [{}]", processorName);
        return multiTenantProcessor;
    }

    /**
     * Builds the base configuration using the same layered approach as the framework's
     * {@link PooledStreamingEventProcessorModule}:
     * <ol>
     *     <li>Start with {@link EventProcessorConfiguration} that includes shared interceptors</li>
     *     <li>Apply shared {@link EventProcessorCustomization} (applies to all processor types)</li>
     *     <li>Apply type-specific {@link PooledStreamingEventProcessorModule.Customization} (applies to all pooled streaming processors)</li>
     *     <li>Apply instance-specific customization</li>
     * </ol>
     * This ensures that configuration set at any level (shared, type-specific, or instance)
     * works seamlessly whether using the standard module or this multi-tenant module.
     */
    private MultiTenantPooledStreamingEventProcessorConfiguration getBaseConfiguration(Configuration config) {
        // Layer 1 & 2: Create base config with shared customization applied
        // This mirrors PooledStreamingEventProcessorModule.defaultEventProcessorsConfiguration()
        MultiTenantPooledStreamingEventProcessorConfiguration baseConfig = new MultiTenantPooledStreamingEventProcessorConfiguration(
                parentSharedCustomizationOrDefault(config)
                        .apply(config, new EventProcessorConfiguration(config)),
                config
        );

        // Layer 3: Apply type-specific customization for pooled streaming processors
        // This picks up any PooledStreamingEventProcessorModule.Customization registered in config
        // The customization mutates the config in-place, so we pass our instance
        typeSpecificCustomizationOrNoOp(config).apply(config, baseConfig);

        // Layer 4: Apply instance-specific customization
        return instanceCustomization.apply(config, baseConfig);
    }

    /**
     * Gets the shared customization that applies to ALL event processor types.
     */
    private static EventProcessorCustomization parentSharedCustomizationOrDefault(Configuration config) {
        return config.getOptionalComponent(EventProcessorCustomization.class)
                     .orElseGet(EventProcessorCustomization::noOp);
    }

    /**
     * Gets the type-specific customization that applies to all pooled streaming processors.
     * This allows users to configure defaults for all pooled streaming processors (both standard and multi-tenant)
     * via {@link PooledStreamingEventProcessorModule.Customization}.
     */
    private static PooledStreamingEventProcessorModule.Customization typeSpecificCustomizationOrNoOp(Configuration config) {
        return config.getOptionalComponent(PooledStreamingEventProcessorModule.Customization.class)
                     .orElseGet(PooledStreamingEventProcessorModule.Customization::noOp);
    }

    private List<EventHandlingComponent> getEventHandlingComponents(Configuration config) {
        return IntStream.range(0, eventHandlingComponentBuilders.size())
                        .mapToObj(i -> {
                            String componentName = processorEventHandlingComponentName(i);
                            return config.getComponent(EventHandlingComponent.class, componentName);
                        })
                        .toList();
    }

    @Nonnull
    private String processorEventHandlingComponentName(int index) {
        return "EventHandlingComponent[" + processorName + "][" + index + "]";
    }

    private ScheduledExecutorService createExecutor(String name) {
        return Executors.newScheduledThreadPool(1, new AxonThreadFactory(name));
    }
}
