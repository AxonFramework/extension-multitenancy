/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterQueue;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterQueueFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryBus;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryUpdateEmitter;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQueryUpdateEmitterSegmentFactory;
import org.axonframework.extensions.multitenancy.components.scheduling.MultiTenantEventScheduler;
import org.axonframework.extensions.multitenancy.components.scheduling.TenantEventSchedulerSegmentFactory;
import org.axonframework.extensions.multitenancy.configuration.MultiTenantEventProcessingModule;
import org.axonframework.extensions.multitenancy.configuration.MultiTenantEventProcessorPredicate;
import org.axonframework.extensions.multitenancy.configuration.MultiTenantStreamableMessageSourceProvider;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.extensions.multitenancy.autoconfig.TenantConfiguration.TENANT_CORRELATION_KEY;

/**
 * Autoconfiguration constructing the
 * {@link org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent multi-tenant aware}
 * infrastructure components.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
@AutoConfiguration
@ConditionalOnProperty(value = "axon.multi-tenancy.enabled", matchIfMissing = true)
@AutoConfigureAfter(MultiTenancyAxonServerAutoConfiguration.class)
public class MultiTenancyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantConnectPredicate tenantFilterPredicate() {
        return tenant -> true;
    }

    @Bean
    @Primary
    @ConditionalOnMissingQualifiedBean(qualifier = "!localSegment", beanClass = CommandBus.class)
    public MultiTenantCommandBus multiTenantCommandBus(TenantCommandSegmentFactory tenantCommandSegmentFactory,
                                                       TargetTenantResolver<?> targetTenantResolver,
                                                       TenantProvider tenantProvider) {
        //noinspection unchecked
        MultiTenantCommandBus commandBus =
                MultiTenantCommandBus.builder()
                                     .tenantSegmentFactory(tenantCommandSegmentFactory)
                                     .targetTenantResolver(
                                             (TargetTenantResolver<CommandMessage<?>>) targetTenantResolver
                                     )
                                     .build();
        tenantProvider.subscribe(commandBus);
        return commandBus;
    }

    @Bean
    @Primary
    public MultiTenantQueryBus multiTenantQueryBus(TenantQuerySegmentFactory tenantQuerySegmentFactory,
                                                   TargetTenantResolver<?> targetTenantResolver,
                                                   TenantProvider tenantProvider) {
        //noinspection unchecked
        MultiTenantQueryBus queryBus =
                MultiTenantQueryBus.builder()
                                   .tenantSegmentFactory(tenantQuerySegmentFactory)
                                   .targetTenantResolver(
                                           (TargetTenantResolver<QueryMessage<?, ?>>) targetTenantResolver
                                   )
                                   .build();
        tenantProvider.subscribe(queryBus);
        return queryBus;
    }

    @Bean
    @Primary
    public QueryUpdateEmitter multiTenantQueryUpdateEmitter(
            TenantQueryUpdateEmitterSegmentFactory tenantQueryUpdateEmitterSegmentFactory,
            TargetTenantResolver<?> targetTenantResolver,
            TenantProvider tenantProvider
    ) {
        //noinspection unchecked
        MultiTenantQueryUpdateEmitter multiTenantQueryUpdateEmitter =
                MultiTenantQueryUpdateEmitter.builder()
                                             .tenantSegmentFactory(tenantQueryUpdateEmitterSegmentFactory)
                                             .targetTenantResolver(
                                                     (TargetTenantResolver<Message<?>>) targetTenantResolver
                                             )
                                             .build();
        tenantProvider.subscribe(multiTenantQueryUpdateEmitter);
        return multiTenantQueryUpdateEmitter;
    }

    @Bean
    @Primary
    public MultiTenantEventStore multiTenantEventStore(TenantEventSegmentFactory tenantEventSegmentFactory,
                                                       TargetTenantResolver<?> targetTenantResolver,
                                                       TenantProvider tenantProvider) {
        //noinspection unchecked
        MultiTenantEventStore multiTenantEventStore =
                MultiTenantEventStore.builder()
                                     .tenantSegmentFactory(tenantEventSegmentFactory)
                                     .targetTenantResolver((TargetTenantResolver<Message<?>>) targetTenantResolver)
                                     .build();
        tenantProvider.subscribe(multiTenantEventStore);
        return multiTenantEventStore;
    }

    @Bean
    @Primary
    public MultiTenantEventScheduler multiTenantEventScheduler(
            TenantEventSchedulerSegmentFactory tenantEventSchedulerSegmentFactory,
            TargetTenantResolver<?> targetTenantResolver,
            TenantProvider tenantProvider
    ) {
        //noinspection unchecked
        MultiTenantEventScheduler multiTenantEventScheduler =
                MultiTenantEventScheduler.builder()
                                         .tenantSegmentFactory(tenantEventSchedulerSegmentFactory)
                                         .targetTenantResolver(
                                                 (TargetTenantResolver<EventMessage<?>>) targetTenantResolver
                                         )
                                         .build();
        tenantProvider.subscribe(multiTenantEventScheduler);
        return multiTenantEventScheduler;
    }

    @Bean
    public MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory(
            TenantProvider tenantProvider,
            TargetTenantResolver<?> targetTenantResolver
    ) {
        Map<String, MultiTenantDeadLetterQueue<EventMessage<?>>> multiTenantDeadLetterQueue = new ConcurrentHashMap<>();
        return (processingGroup) -> multiTenantDeadLetterQueue.computeIfAbsent(processingGroup, (key) -> {
            //noinspection unchecked
            MultiTenantDeadLetterQueue<EventMessage<?>> deadLetterQueue =
                    MultiTenantDeadLetterQueue.builder()
                                              .targetTenantResolver(
                                                      (TargetTenantResolver<EventMessage<?>>) targetTenantResolver
                                              )
                                              .processingGroup(processingGroup)
                                              .build();
            tenantProvider.subscribe(deadLetterQueue);
            return deadLetterQueue;
        });
    }


    @Bean
    @ConditionalOnMissingBean
    public MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider() {
        return (defaultTenantSource, processorName, tenantDescriptor, configuration) -> defaultTenantSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTenantEventProcessorPredicate multiTenantEventProcessorPredicate() {
        return (processorName) -> true;
    }

    @Bean
    public MultiTenantEventProcessingModule multiTenantEventProcessingModule(
            TenantProvider tenantProvider,
            MultiTenantStreamableMessageSourceProvider multiTenantStreamableMessageSourceProvider,
            MultiTenantDeadLetterQueueFactory<EventMessage<?>> multiTenantDeadLetterQueueFactory,
            MultiTenantEventProcessorPredicate multiTenantEventProcessorPredicate
    ) {
        return new MultiTenantEventProcessingModule(
                tenantProvider,
                multiTenantStreamableMessageSourceProvider,
                multiTenantDeadLetterQueueFactory,
                multiTenantEventProcessorPredicate
        );
    }

    @Bean
    @ConditionalOnProperty(name = "axon.multi-tenancy.use-metadata-helper", matchIfMissing = true)
    public TargetTenantResolver<Message<?>> targetTenantResolver() {
        return (message, tenants) ->
                TenantDescriptor.tenantWithId(
                        (String) message.getMetaData()
                                        .getOrDefault(TENANT_CORRELATION_KEY, "unknownTenant")
                );
    }

    @Bean
    @ConditionalOnProperty(name = "axon.multi-tenancy.use-metadata-helper", matchIfMissing = true)
    public CorrelationDataProvider tenantCorrelationProvider() {
        return new TenantCorrelationProvider(TENANT_CORRELATION_KEY);
    }
}
