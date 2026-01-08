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
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.messaging.core.annotation.TenantComponentResolverFactory;
import org.axonframework.extension.multitenancy.messaging.core.unitofwork.annotation.TenantAwareProcessingContextResolverFactory;
import org.axonframework.extension.multitenancy.spring.data.jpa.TenantDataSourceProvider;
import org.axonframework.extension.multitenancy.spring.data.jpa.TenantEntityManagerFactoryBuilder;
import org.axonframework.extension.multitenancy.spring.data.jpa.TenantJpaRepositoryFactory;
import org.axonframework.extension.multitenancy.spring.data.jpa.TenantTransactionManagerBuilder;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.core.configuration.reflection.ParameterResolverFactoryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto-configuration for multi-tenant Spring Data JPA support.
 * <p>
 * This configuration is activated when {@code axon.multi-tenancy.jpa.tenant-repositories=true}.
 * When enabled, it:
 * <ol>
 *     <li>Requires a {@link TenantDataSourceProvider} bean to be present</li>
 *     <li>Creates a {@link TenantEntityManagerFactoryBuilder} for building tenant-specific EntityManagerFactories</li>
 *     <li>Creates a {@link TenantTransactionManagerBuilder} for building tenant-specific TransactionManagers</li>
 *     <li>Scans for all Spring Data repository interfaces (extending {@link Repository})</li>
 *     <li>Registers each repository as a tenant component for automatic tenant-scoped injection</li>
 * </ol>
 * <p>
 * When this property is enabled, Spring Boot's default JPA autoconfiguration (HibernateJpaAutoConfiguration,
 * JpaRepositoriesAutoConfiguration) is automatically excluded by {@link MultiTenancyAutoConfigurationImportFilter}.
 * This means you don't need any special annotations - just define your repository interfaces as usual:
 * <pre>{@code
 * public interface OrderRepository extends JpaRepository<Order, String> {
 *     List<Order> findByCustomerId(String customerId);
 * }
 *
 * @Component
 * public class OrderProjector {
 *     @EventHandler
 *     public void on(OrderCreatedEvent event, OrderRepository repository) {
 *         // repository is automatically scoped to the tenant from event metadata
 *         repository.save(new Order(event.orderId(), event.customerId()));
 *     }
 * }
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantDataSourceProvider
 * @see TenantEntityManagerFactoryBuilder
 * @see MultiTenancyAutoConfigurationImportFilter
 */
@AutoConfiguration(after = MultiTenancyAutoConfiguration.class)
@ConditionalOnClass(JpaRepository.class)
@ConditionalOnBean(TenantDataSourceProvider.class)
@ConditionalOnProperty(value = "axon.multi-tenancy.jpa.tenant-repositories", havingValue = "true")
@EnableConfigurationProperties(MultiTenancyProperties.class)
public class MultiTenancySpringDataJpaAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenancySpringDataJpaAutoConfiguration.class);

    /**
     * Creates a {@link TenantEntityManagerFactoryBuilder} for building tenant-specific EntityManagerFactories.
     *
     * @param dataSourceProvider the provider for tenant-specific DataSources
     * @return a builder for tenant-specific EntityManagerFactories
     */
    @Bean
    @ConditionalOnMissingBean(TenantEntityManagerFactoryBuilder.class)
    public TenantEntityManagerFactoryBuilder tenantEntityManagerFactoryBuilder(
            TenantDataSourceProvider dataSourceProvider) {
        return TenantEntityManagerFactoryBuilder
                .forDataSourceProvider(dataSourceProvider)
                .build();
    }

    /**
     * Creates a {@link TenantTransactionManagerBuilder} for building tenant-specific TransactionManagers.
     * <p>
     * Each tenant's {@link jakarta.persistence.EntityManagerFactory} requires its own
     * {@link org.springframework.orm.jpa.JpaTransactionManager} to properly manage transactions.
     *
     * @param emfBuilder the tenant EntityManagerFactory builder
     * @return a builder for tenant-specific TransactionManagers
     */
    @Bean
    @ConditionalOnMissingBean(TenantTransactionManagerBuilder.class)
    public TenantTransactionManagerBuilder tenantTransactionManagerBuilder(
            TenantEntityManagerFactoryBuilder emfBuilder) {
        return TenantTransactionManagerBuilder
                .forEntityManagerFactoryBuilder(emfBuilder)
                .build();
    }

    /**
     * Creates a {@link ConfigurationEnhancer} that registers Spring Data repository interfaces
     * as tenant components.
     * <p>
     * This enhancer scans the classpath for interfaces extending {@link Repository},
     * using the same base packages as Spring Boot's auto-configuration.
     *
     * @param emfBuilder  the tenant EntityManagerFactory builder
     * @param txBuilder   the tenant TransactionManager builder
     * @param beanFactory the bean factory to get auto-configuration packages
     * @return a configuration enhancer for tenant repository registration
     */
    @Bean
    public ConfigurationEnhancer tenantRepositoryConfigurationEnhancer(
            TenantEntityManagerFactoryBuilder emfBuilder,
            TenantTransactionManagerBuilder txBuilder,
            BeanFactory beanFactory) {

        // Get base packages from Spring Boot's auto-configuration
        List<String> basePackages = AutoConfigurationPackages.has(beanFactory)
                ? AutoConfigurationPackages.get(beanFactory)
                : List.of();

        // Scan for Spring Data repository interfaces
        Set<Class<?>> repositoryTypes = scanForRepositories(basePackages);

        if (repositoryTypes.isEmpty()) {
            logger.debug("No Spring Data repository interfaces found in packages: {}", basePackages);
            return componentRegistry -> { /* no-op */ };
        }

        logger.debug("Found {} Spring Data repository interfaces for tenant component registration",
                repositoryTypes.size());

        return new TenantRepositoryEnhancer(repositoryTypes, emfBuilder, txBuilder);
    }

    /**
     * Scans the classpath for interfaces extending {@link Repository}.
     * <p>
     * This finds all Spring Data repository interfaces including those extending
     * {@code JpaRepository}, {@code CrudRepository}, {@code PagingAndSortingRepository}, etc.
     */
    private Set<Class<?>> scanForRepositories(List<String> basePackages) {
        Set<Class<?>> repositoryTypes = new HashSet<>();

        if (basePackages.isEmpty()) {
            logger.warn("No base packages configured for repository scanning. " +
                    "Ensure your application has @EnableAutoConfiguration or @SpringBootApplication.");
            return repositoryTypes;
        }

        // Create scanner that can find interfaces (not just concrete classes)
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        // Allow interfaces (default implementation only allows concrete classes)
                        return beanDefinition.getMetadata().isInterface()
                                || super.isCandidateComponent(beanDefinition);
                    }
                };
        // Scan for any interface extending Spring Data's Repository
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        for (String basePackage : basePackages) {
            logger.debug("Scanning package {} for Spring Data repositories", basePackage);
            for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
                try {
                    Class<?> repositoryType = Class.forName(bd.getBeanClassName());
                    // Skip the Repository interface itself and Spring's built-in interfaces
                    if (isUserDefinedRepository(repositoryType)) {
                        repositoryTypes.add(repositoryType);
                        logger.debug("Discovered repository: {}", repositoryType.getName());
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn("Could not load repository class: {}", bd.getBeanClassName(), e);
                }
            }
        }

        return repositoryTypes;
    }

    /**
     * Checks if a repository type is a user-defined repository (not a Spring Data built-in).
     */
    private boolean isUserDefinedRepository(Class<?> repositoryType) {
        // Skip Spring Data's own interfaces
        String packageName = repositoryType.getPackageName();
        return !packageName.startsWith("org.springframework.data");
    }

    /**
     * A {@link ConfigurationEnhancer} that registers Spring Data repository interfaces
     * as tenant components.
     */
    private static class TenantRepositoryEnhancer implements ConfigurationEnhancer {

        private final Set<Class<?>> repositoryTypes;
        private final TenantEntityManagerFactoryBuilder emfBuilder;
        private final TenantTransactionManagerBuilder txBuilder;

        TenantRepositoryEnhancer(Set<Class<?>> repositoryTypes,
                                 TenantEntityManagerFactoryBuilder emfBuilder,
                                 TenantTransactionManagerBuilder txBuilder) {
            this.repositoryTypes = repositoryTypes;
            this.emfBuilder = emfBuilder;
            this.txBuilder = txBuilder;
        }

        @Override
        public int order() {
            // Run after MultiTenancyConfigurationDefaults (which is MAX_VALUE - 1)
            return Integer.MAX_VALUE - 2;
        }

        @Override
        public void enhance(@Nonnull ComponentRegistry componentRegistry) {
            logger.debug("Registering {} Spring Data repositories as tenant components", repositoryTypes.size());

            // Collect tenant component registrations
            List<TenantComponentRegistration<?>> registrations = new ArrayList<>();
            for (Class<?> repositoryType : repositoryTypes) {
                registrations.add(createRegistration(repositoryType));
            }

            // Register a ParameterResolverFactory that creates tenant-scoped repositories
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

                        // Register all repository types in the factory
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
        private <T> TenantComponentRegistration<T> createRegistration(Class<T> repositoryType) {
            TenantComponentFactory<T> factory = TenantJpaRepositoryFactory.forRepository(repositoryType, emfBuilder, txBuilder);
            return new TenantComponentRegistration<>(repositoryType, factory);
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
