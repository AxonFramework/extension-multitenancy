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
package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.extension.spring.config.SpringEventSourcedEntityLookup;
import org.axonframework.extensions.multitenancy.messaging.eventhandling.processing.TenantTokenStoreFactory;
import org.axonframework.extensions.multitenancy.messaging.eventhandling.processing.InMemoryTenantTokenStoreFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * Auto-configuration for multi-tenant event processing.
 * <p>
 * This configuration provides:
 * <ul>
 *     <li>{@link MultiTenantMessageHandlerLookup} - replaces standard message handler lookup
 *         to create multi-tenant event processors instead of standard ones</li>
 *     <li>Default {@link TenantTokenStoreFactory} - provides in-memory token stores per tenant
 *         (override with JPA or JDBC factory for production use)</li>
 * </ul>
 * <p>
 * This configuration is activated when multi-tenancy is enabled
 * ({@code axon.multi-tenancy.enabled=true} or missing). The {@link MultiTenantMessageHandlerConfigurer}
 * will check at runtime whether to create multi-tenant processors based on
 * {@link org.axonframework.extensions.multitenancy.core.configuration.MultiTenantEventProcessorPredicate}.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see MultiTenantMessageHandlerConfigurer
 * @see MultiTenantMessageHandlerLookup
 */
@AutoConfiguration
@ConditionalOnProperty(value = "axon.multi-tenancy.enabled", matchIfMissing = true)
@AutoConfigureBefore(name = "org.axonframework.extension.springboot.autoconfig.EventProcessingAutoConfiguration")
public class MultiTenantEventProcessingAutoConfiguration {

    /**
     * Creates the multi-tenant message handler lookup that replaces the standard
     * Spring extension's message handler lookup.
     * <p>
     * This lookup creates {@link MultiTenantMessageHandlerConfigurer} instances
     * that produce multi-tenant event processors.
     *
     * @return the multi-tenant message handler lookup
     */
    @Bean
    public MultiTenantMessageHandlerLookup multiTenantMessageHandlerLookup() {
        return new MultiTenantMessageHandlerLookup();
    }

    /**
     * Creates a default in-memory tenant token store factory.
     * <p>
     * This is suitable for testing and development. For production use,
     * provide a JPA or JDBC based {@link TenantTokenStoreFactory}.
     *
     * @return the default in-memory tenant token store factory
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantTokenStoreFactory tenantTokenStoreFactory() {
        return new InMemoryTenantTokenStoreFactory();
    }

    /**
     * Provides the Spring event-sourced entity lookup.
     * <p>
     * This bean is normally provided by the framework's {@code InfrastructureAutoConfiguration},
     * but since we exclude that configuration when multi-tenancy is enabled (to replace
     * {@code MessageHandlerLookup} with our multi-tenant version), we need to provide this
     * bean ourselves.
     * <p>
     * The {@code SpringEventSourcedEntityLookup} scans for {@code @EventSourced} annotated
     * classes and registers them with the Axon configuration via
     * {@code SpringEventSourcedEntityConfigurer}.
     *
     * @return the Spring event-sourced entity lookup
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringEventSourcedEntityLookup springEventSourcedEntityLookup() {
        return new SpringEventSourcedEntityLookup();
    }
}
