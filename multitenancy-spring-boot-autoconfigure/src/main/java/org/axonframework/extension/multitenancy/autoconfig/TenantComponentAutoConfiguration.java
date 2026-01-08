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
package org.axonframework.extension.multitenancy.autoconfig;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantComponentFactory;
import org.axonframework.extension.multitenancy.core.TenantComponentRegistry;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.extension.multitenancy.messaging.core.unitofwork.annotation.TenantAwareProcessingContextResolverFactory;
import org.axonframework.extension.multitenancy.spring.TenantComponent;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.core.configuration.reflection.ParameterResolverFactoryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-configuration for automatic discovery and registration of {@link TenantComponent} implementations.
 * <p>
 * This configuration scans the classpath for classes implementing {@link TenantComponent},
 * instantiates them with Spring dependency injection (without registering them as Spring beans),
 * and registers them as tenant components for injection into message handlers.
 * <p>
 * Classes implementing {@link TenantComponent} will:
 * <ul>
 *     <li>Receive Spring dependencies through constructor injection</li>
 *     <li>NOT be available for autowiring in other Spring beans</li>
 *     <li>Only be injectable into Axon message handlers or accessible via {@code TenantAwareProcessingContext}</li>
 * </ul>
 * <p>
 * This auto-configuration is enabled by default. To disable it, set
 * {@code axon.multi-tenancy.tenant-components.enabled=false}.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Note: No @Component annotation!
 * public class TenantOrderService implements TenantComponent<TenantOrderService> {
 *     private final EmailService emailService;
 *     private final String tenantId;
 *
 *     public TenantOrderService(EmailService emailService) {
 *         this.emailService = emailService;
 *         this.tenantId = null;
 *     }
 *
 *     private TenantOrderService(EmailService emailService, String tenantId) {
 *         this.emailService = emailService;
 *         this.tenantId = tenantId;
 *     }
 *
 *     @Override
 *     public TenantOrderService createForTenant(TenantDescriptor tenant) {
 *         return new TenantOrderService(emailService, tenant.tenantId());
 *     }
 * }
 *
 * // In a message handler:
 * @EventHandler
 * public void handle(OrderPlaced event, TenantOrderService orderService) {
 *     // orderService is automatically the tenant-specific instance
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantComponent
 */
@AutoConfiguration(after = MultiTenancyAutoConfiguration.class)
@ConditionalOnProperty(value = "axon.multi-tenancy.tenant-components.enabled", matchIfMissing = true)
public class TenantComponentAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TenantComponentAutoConfiguration.class);

    /**
     * Creates a {@link ConfigurationEnhancer} that discovers and registers {@link TenantComponent}
     * implementations as tenant-scoped components.
     *
     * @param applicationContext the Spring application context for classpath scanning and bean creation
     * @param beanFactory        the bean factory to get auto-configuration packages
     * @return a configuration enhancer for tenant component registration
     */
    @Bean
    public ConfigurationEnhancer tenantComponentConfigurationEnhancer(
            ApplicationContext applicationContext,
            BeanFactory beanFactory) {

        // Fail fast if someone accidentally annotated a TenantComponent with @Component
        Map<String, TenantComponent> accidentalBeans = applicationContext.getBeansOfType(TenantComponent.class);
        if (!accidentalBeans.isEmpty()) {
            throw new IllegalStateException(
                    "TenantComponent implementations must NOT be registered as Spring beans. " +
                    "Remove @Component, @Service, or similar annotations from: " + accidentalBeans.keySet() +
                    ". TenantComponent instances are managed by Axon's multi-tenancy infrastructure and " +
                    "should only be injected into message handlers or accessed via TenantAwareProcessingContext."
            );
        }

        // Get base packages from Spring Boot's auto-configuration
        List<String> basePackages = AutoConfigurationPackages.has(beanFactory)
                ? AutoConfigurationPackages.get(beanFactory)
                : List.of();

        // Scan for TenantComponent implementations
        Set<Class<?>> tenantComponentClasses = scanForTenantComponents(basePackages);

        if (tenantComponentClasses.isEmpty()) {
            logger.debug("No TenantComponent implementations found in packages: {}", basePackages);
            return componentRegistry -> { /* no-op */ };
        }

        logger.debug("Found {} TenantComponent implementations for registration", tenantComponentClasses.size());

        // Create factory instances with Spring DI (but not registered as beans)
        List<TenantComponentHolder<?>> holders = createFactoryInstances(applicationContext, tenantComponentClasses);

        return new TenantComponentEnhancer(holders);
    }

    /**
     * Scans the classpath for classes implementing {@link TenantComponent}.
     */
    private Set<Class<?>> scanForTenantComponents(List<String> basePackages) {
        Set<Class<?>> tenantComponentClasses = new HashSet<>();

        if (basePackages.isEmpty()) {
            logger.warn("No base packages configured for TenantComponent scanning. " +
                    "Ensure your application has @EnableAutoConfiguration or @SpringBootApplication.");
            return tenantComponentClasses;
        }

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(TenantComponent.class));

        for (String basePackage : basePackages) {
            logger.debug("Scanning package {} for TenantComponent implementations", basePackage);
            for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
                try {
                    Class<?> componentClass = Class.forName(bd.getBeanClassName());
                    // Skip the interface itself
                    if (componentClass != TenantComponent.class && !componentClass.isInterface()) {
                        tenantComponentClasses.add(componentClass);
                        logger.debug("Discovered TenantComponent: {}", componentClass.getName());
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn("Could not load TenantComponent class: {}", bd.getBeanClassName(), e);
                }
            }
        }

        return tenantComponentClasses;
    }

    /**
     * Creates factory instances with Spring DI but without registering them as beans.
     */
    private List<TenantComponentHolder<?>> createFactoryInstances(
            ApplicationContext applicationContext,
            Set<Class<?>> tenantComponentClasses) {

        List<TenantComponentHolder<?>> holders = new ArrayList<>();
        AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();

        for (Class<?> componentClass : tenantComponentClasses) {
            try {
                // Create instance with Spring DI, but don't register as a bean
                @SuppressWarnings("unchecked")
                TenantComponent<Object> factoryInstance = (TenantComponent<Object>)
                        beanFactory.createBean(componentClass, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false);

                // Resolve the generic type parameter T
                Class<?> componentType = GenericTypeResolver.resolveTypeArgument(
                        componentClass, TenantComponent.class);

                if (componentType == null) {
                    logger.warn("Could not resolve component type for TenantComponent: {}. " +
                            "Ensure the class directly specifies the type parameter.", componentClass.getName());
                    continue;
                }

                @SuppressWarnings({"unchecked", "rawtypes"})
                TenantComponentHolder<?> holder = new TenantComponentHolder(componentType, factoryInstance);
                holders.add(holder);
                logger.debug("Created factory instance for TenantComponent: {} -> {}",
                        componentClass.getName(), componentType.getName());

            } catch (Exception e) {
                logger.error("Failed to create factory instance for TenantComponent: {}",
                        componentClass.getName(), e);
            }
        }

        return holders;
    }

    /**
     * Wraps a {@link TenantComponent} as a {@link TenantComponentFactory}.
     */
    @SuppressWarnings("unchecked")
    private static <T> TenantComponentFactory<T> wrapAsFactory(TenantComponent<T> instance) {
        return new TenantComponentFactory<>() {
            @Override
            public T apply(TenantDescriptor tenant) {
                return instance.createForTenant(tenant);
            }

            @Override
            public void cleanup(TenantDescriptor tenant, T component) {
                instance.cleanupForTenant(tenant, component);
            }
        };
    }

    /**
     * Holds a tenant component factory instance along with its resolved component type.
     */
    private record TenantComponentHolder<T>(Class<T> componentType, TenantComponent<T> factoryInstance) {
    }

    /**
     * A {@link ConfigurationEnhancer} that registers {@link TenantComponent} implementations
     * as tenant-scoped components.
     */
    private static class TenantComponentEnhancer implements ConfigurationEnhancer {

        private final List<TenantComponentHolder<?>> holders;

        TenantComponentEnhancer(List<TenantComponentHolder<?>> holders) {
            this.holders = holders;
        }

        @Override
        public int order() {
            // Run after MultiTenancyConfigurationDefaults (which is MAX_VALUE - 1)
            return Integer.MAX_VALUE - 2;
        }

        @Override
        public void enhance(@Nonnull ComponentRegistry componentRegistry) {
            logger.debug("Registering {} TenantComponent implementations as tenant components", holders.size());

            // Create registrations from holders
            List<TenantComponentRegistration<?>> registrations = new ArrayList<>();
            for (TenantComponentHolder<?> holder : holders) {
                registrations.add(createRegistration(holder));
            }

            // Register a ParameterResolverFactory that creates tenant-scoped components
            ParameterResolverFactoryUtils.registerToComponentRegistry(
                    componentRegistry,
                    config -> {
                        @SuppressWarnings("unchecked")
                        TargetTenantResolver<Message> tenantResolver =
                                config.getComponent(TargetTenantResolver.class);

                        // Create the TenantComponentResolverFactory
                        TenantComponentResolverFactory componentFactory =
                                new TenantComponentResolverFactory(tenantResolver);

                        // Get tenant provider for lifecycle management
                        TenantProvider tenantProvider = config.getOptionalComponent(TenantProvider.class)
                                .orElse(null);

                        // Register all component types in the factory
                        for (TenantComponentRegistration<?> registration : registrations) {
                            registerTenantComponent(componentFactory, registration, tenantProvider);
                        }

                        // Create the TenantAwareProcessingContextResolverFactory
                        TenantAwareProcessingContextResolverFactory contextFactory =
                                new TenantAwareProcessingContextResolverFactory(componentFactory, tenantResolver);

                        // Return a MultiParameterResolverFactory containing both
                        return MultiParameterResolverFactory.ordered(componentFactory, contextFactory);
                    }
            );
        }

        @SuppressWarnings("unchecked")
        private <T> TenantComponentRegistration<T> createRegistration(TenantComponentHolder<T> holder) {
            TenantComponentFactory<T> factory = wrapAsFactory(holder.factoryInstance());
            return new TenantComponentRegistration<>(holder.componentType(), factory);
        }

        private <T> void registerTenantComponent(
                TenantComponentResolverFactory componentFactory,
                TenantComponentRegistration<T> registration,
                TenantProvider tenantProvider) {

            TenantComponentRegistry<T> registry = componentFactory.registerComponent(
                    registration.componentType(),
                    registration.factory()
            );

            if (tenantProvider != null) {
                tenantProvider.subscribe(registry);
                tenantProvider.getTenants().forEach(registry::registerTenant);
            }

            logger.debug("Registered tenant component: {}", registration.componentType().getName());
        }
    }

    /**
     * Holds a tenant component registration: the type and its factory.
     */
    private record TenantComponentRegistration<T>(Class<T> componentType, TenantComponentFactory<T> factory) {
    }
}
