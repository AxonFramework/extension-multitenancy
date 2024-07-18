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

import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceFactory;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.deadletterqueue.MultiTenantDeadLetterQueueFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryBus;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryUpdateEmitter;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.components.scheduling.MultiTenantEventScheduler;
import org.axonframework.extensions.multitenancy.configuration.MultiTenantEventProcessingModule;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.springboot.autoconfig.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the multi-tenancy auto-configuration.
 *
 * @author Stefan Dragisic
 */
class MultiTenancyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(
                            AxonAutoConfiguration.class,
                            EventProcessingAutoConfiguration.class,
                            InfraConfiguration.class,
                            AxonServerBusAutoConfiguration.class,
                            AxonServerAutoConfiguration.class,
                            NoOpTransactionAutoConfiguration.class,
                            ObjectMapperAutoConfiguration.class,
                            TransactionAutoConfiguration.class,
                            XStreamAutoConfiguration.class,
                            AxonTracingAutoConfiguration.class
                    ));

    @Test
    void multiTenancyAutoConfiguration() {
        contextRunner.withConfiguration(AutoConfigurations.of(MultiTenancyAxonServerAutoConfiguration.class))
                     .withConfiguration(AutoConfigurations.of(MultiTenancyAutoConfiguration.class))
                     .withPropertyValues("axon.axonserver.contexts=tenant-1,tenant-2")
                     .run(context -> {
                         assertThat(context).getBean("tenantFilterPredicate")
                                            .isInstanceOf(TenantConnectPredicate.class);
                         assertThat(context).getBean("targetTenantResolver")
                                            .isInstanceOf(TargetTenantResolver.class);
                         assertThat(context).getBean("tenantCorrelationProvider")
                                            .isInstanceOf(CorrelationDataProvider.class);
                         assertThat(context).getBean("multiTenantEventProcessingModule")
                                            .isExactlyInstanceOf(MultiTenantEventProcessingModule.class);
                         assertThat(context).getBean("multiTenantCommandBus")
                                            .isExactlyInstanceOf(MultiTenantCommandBus.class);
                         assertThat(context).getBean("multiTenantQueryBus")
                                            .isExactlyInstanceOf(MultiTenantQueryBus.class);
                         assertThat(context).getBean("multiTenantEventStore")
                                            .isExactlyInstanceOf(MultiTenantEventStore.class);
                         assertThat(context).getBean("multiTenantDeadLetterQueueFactory")
                                            .isInstanceOf(MultiTenantDeadLetterQueueFactory.class);
                         assertThat(context).getBean("multiTenantEventScheduler")
                                            .isExactlyInstanceOf(MultiTenantEventScheduler.class);
                         assertThat(context).getBean("multiTenantQueryUpdateEmitter")
                                            .isInstanceOf(MultiTenantQueryUpdateEmitter.class);
                         assertThat(context).getBean("persistentStreamMessageSourceFactory")
                                 .isInstanceOf(PersistentStreamMessageSourceFactory.class);
                         assertThat(context).getBean("tenantPersistentStreamMessageSourceFactory")
                                 .isInstanceOf(TenantPersistentStreamMessageSourceFactory.class);
                     });
    }

    @Test
    void multiTenancyDisabled() {
        contextRunner.withPropertyValues(
                             "axon.multi-tenancy.enabled:false", "axon.axonserver.contexts=tenant-1,tenant-2"
                     )
                     .withConfiguration(AutoConfigurations.of(MultiTenancyAxonServerAutoConfiguration.class))
                     .withConfiguration(AutoConfigurations.of(MultiTenancyAutoConfiguration.class))
                     .run(context -> {
                         assertThat(context).doesNotHaveBean(TenantConnectPredicate.class);
                         assertThat(context).doesNotHaveBean(TargetTenantResolver.class);
                         assertThat(context).doesNotHaveBean(MultiTenantEventProcessingModule.class);
                         assertThat(context).doesNotHaveBean(MultiTenantCommandBus.class);
                         assertThat(context).doesNotHaveBean(MultiTenantEventStore.class);
                         assertThat(context).doesNotHaveBean(MultiTenantQueryBus.class);
                         assertThat(context).doesNotHaveBean(AxonServerTenantProvider.class);
                         assertThat(context).doesNotHaveBean(TenantCommandSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(TenantQuerySegmentFactory.class);
                         assertThat(context).doesNotHaveBean(MultiTenantQueryUpdateEmitter.class);
                         assertThat(context).doesNotHaveBean(TenantEventSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(MultiTenantDeadLetterQueueFactory.class);
                         assertThat(context).doesNotHaveBean(MultiTenantEventScheduler.class);
                         assertThat(context).doesNotHaveBean(MultiTenantQueryUpdateEmitter.class);
                         assertThat(context).doesNotHaveBean(TenantPersistentStreamMessageSourceFactory.class);
                     });
    }

    @Test
    void multiTenancyMetaDataHelperDisabled() {
        TargetTenantResolver<Message<?>> userResolver = (message, tenants) ->
                TenantDescriptor.tenantWithId(
                        (String) message.getMetaData()
                                        .getOrDefault("USER_CORRELATION_KEY", "unknownTenant")
                );
        contextRunner.withBean(TargetTenantResolver.class, () -> userResolver)
                     .withPropertyValues(
                             "axon.multi-tenancy.use-metadata-helper:false",
                             "axon.axonserver.contexts=tenant-1,tenant-2"
                     )
                     .withConfiguration(AutoConfigurations.of(MultiTenancyAxonServerAutoConfiguration.class))
                     .withConfiguration(AutoConfigurations.of(MultiTenancyAutoConfiguration.class))
                     .run(context -> {
                         assertThat(context).doesNotHaveBean("tenantCorrelationProvider");
                         assertThat(context).getBeanNames(TargetTenantResolver.class)
                                            .hasSize(1);
                         assertThat(context).getBean("targetTenantResolver")
                                            .returns(TargetTenantResolver.class, ttr -> {
                                                assertEquals(ttr, userResolver);
                                                return TargetTenantResolver.class;
                                            });
                     });
    }
}


