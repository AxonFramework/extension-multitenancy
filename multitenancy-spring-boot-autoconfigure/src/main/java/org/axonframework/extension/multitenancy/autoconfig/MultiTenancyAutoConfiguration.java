/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extension.multitenancy.autoconfig;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extension.multitenancy.core.MetadataBasedTenantResolver;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantConnectPredicate;
import org.axonframework.extension.multitenancy.core.configuration.MultiTenantEventProcessorPredicate;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;
import org.axonframework.messaging.eventhandling.EventSink;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for multi-tenant Axon Framework support beans.
 * <p>
 * This configuration provides:
 * <ul>
 *     <li>Property binding via {@link MultiTenancyProperties}</li>
 *     <li>Default {@link TargetTenantResolver} using message metadata</li>
 *     <li>Default {@link TenantConnectPredicate} accepting all tenants</li>
 *     <li>{@link CorrelationDataProvider} for tenant ID propagation</li>
 *     <li>{@link MultiTenantEventProcessorPredicate} for event processor configuration</li>
 * </ul>
 * <p>
 * The actual multi-tenant infrastructure components (MultiTenantCommandBus, MultiTenantQueryBus,
 * MultiTenantEventStore) are created by the {@code MultiTenancyConfigurationDefaults} SPI enhancer
 * in the core module. This autoconfiguration only provides the supporting beans that the SPI
 * enhancer uses.
 * <p>
 * For Axon Server deployments, the {@code DistributedMultiTenancyConfigurationDefaults} in the
 * {@code multitenancy-axon-server-connector} module provides additional components like
 * {@code AxonServerTenantProvider} and multi-tenant connectors.
 * <p>
 * Multi-tenancy can be disabled by setting {@code axon.multi-tenancy.enabled=false}.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see MultiTenancyProperties
 * @see org.axonframework.extension.multitenancy.core.configuration.MultiTenancyConfigurationDefaults
 */
@AutoConfiguration
@ConditionalOnProperty(value = "axon.multi-tenancy.enabled", matchIfMissing = true)
@AutoConfigureAfter(MultiTenancyAxonServerAutoConfiguration.class)
@EnableConfigurationProperties(MultiTenancyProperties.class)
public class MultiTenancyAutoConfiguration {

    /**
     * Creates a default {@link TenantConnectPredicate} that accepts all tenants.
     * <p>
     * Users can override this bean to filter which tenants should be connected.
     *
     * @return a predicate that returns {@code true} for all tenants
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantConnectPredicate tenantFilterPredicate() {
        return tenant -> true;
    }

    /**
     * Creates a predicate that determines whether multi-tenancy should be enabled
     * for a given event processor.
     * <p>
     * By default, enables multi-tenancy for all processors. Users can override
     * this bean to selectively enable/disable multi-tenancy per processor.
     *
     * @return a predicate that enables multi-tenancy for all processors
     */
    @Bean
    @ConditionalOnMissingBean
    public MultiTenantEventProcessorPredicate multiTenantEventProcessorPredicate() {
        return MultiTenantEventProcessorPredicate.enableMultiTenancy();
    }

    /**
     * Creates the target tenant resolver that extracts the tenant from message metadata.
     * <p>
     * Uses the {@code tenantKey} property from {@link MultiTenancyProperties} to determine
     * which metadata key contains the tenant identifier. Defaults to "tenantId".
     * <p>
     * This resolver is used by the SPI-registered multi-tenant components to route
     * messages to the appropriate tenant.
     *
     * @param properties the multi-tenancy configuration properties
     * @return the metadata-based tenant resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public TargetTenantResolver<Message> targetTenantResolver(MultiTenancyProperties properties) {
        return new MetadataBasedTenantResolver(properties.getTenantKey());
    }

    /**
     * Creates the correlation data provider that propagates tenant information
     * to new messages dispatched during message handling.
     * <p>
     * Uses the {@code tenantKey} property from {@link MultiTenancyProperties} to determine
     * which metadata key to propagate. This ensures that when a command handler dispatches
     * events or other commands, the tenant ID is automatically included in the new messages.
     *
     * @param properties the multi-tenancy configuration properties
     * @return the tenant correlation provider
     */
    @Bean
    @ConditionalOnMissingBean(TenantCorrelationProvider.class)
    public CorrelationDataProvider tenantCorrelationProvider(MultiTenancyProperties properties) {
        return new TenantCorrelationProvider(properties.getTenantKey());
    }

    /**
     * Provides the primary {@link EventSink} bean to resolve ambiguity when multiple beans
     * implement {@code EventSink}.
     * <p>
     * This is necessary because when multi-tenancy is enabled, both the decorated
     * {@link EventStore} and the {@code TenantEventStoreProvider} component are exposed
     * as Spring beans by the {@code SpringComponentRegistry}. Since {@code MultiTenantEventStore}
     * implements both interfaces, Spring sees duplicate candidates for {@code EventSink}.
     * <p>
     * This bean delegates to the Axon {@link Configuration}'s {@link EventStore}, ensuring
     * the properly decorated event store is used as the primary {@code EventSink}.
     *
     * @param configuration the Axon configuration
     * @return the primary event sink (the configured event store)
     */
    @Bean
    @Primary
    public EventSink primaryEventSink(Configuration configuration) {
        return configuration.getComponent(EventStore.class);
    }
}
