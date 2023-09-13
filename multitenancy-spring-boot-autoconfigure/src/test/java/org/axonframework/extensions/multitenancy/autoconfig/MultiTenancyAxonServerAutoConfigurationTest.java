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

import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQueryUpdateEmitterSegmentFactory;
import org.axonframework.extensions.multitenancy.components.scheduling.TenantEventSchedulerSegmentFactory;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonTracingAutoConfiguration;
import org.axonframework.springboot.autoconfig.EventProcessingAutoConfiguration;
import org.axonframework.springboot.autoconfig.InfraConfiguration;
import org.axonframework.springboot.autoconfig.NoOpTransactionAutoConfiguration;
import org.axonframework.springboot.autoconfig.ObjectMapperAutoConfiguration;
import org.axonframework.springboot.autoconfig.TransactionAutoConfiguration;
import org.axonframework.springboot.autoconfig.XStreamAutoConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the autoconfiguration of Axon Server-specific multi-tenancy components.
 *
 * @author Stefan Dragisic
 */
class MultiTenancyAxonServerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(
                            AxonAutoConfiguration.class,
                            EventProcessingAutoConfiguration.class,
                            InfraConfiguration.class,
                            AxonServerBusAutoConfiguration.class,
                            MultiTenancyAxonServerAutoConfiguration.class,
                            AxonServerAutoConfiguration.class,
                            NoOpTransactionAutoConfiguration.class,
                            ObjectMapperAutoConfiguration.class,
                            TransactionAutoConfiguration.class,
                            XStreamAutoConfiguration.class,
                            AxonTracingAutoConfiguration.class
                    ));

    @Test
    void axonServerAutoConfiguration() {
        contextRunner.withConfiguration(AutoConfigurations.of(MultiTenancyAxonServerAutoConfiguration.class))
                     .withConfiguration(AutoConfigurations.of(MultiTenancyAutoConfiguration.class))
                     .withPropertyValues("axon.axonserver.contexts=tenant-1,tenant-2")
                     .run(context -> {
                         assertThat(context).getBean("tenantEventSchedulerSegmentFactory")
                                            .isInstanceOf(TenantEventSchedulerSegmentFactory.class);
                         assertThat(context).getBean("tenantAxonServerCommandSegmentFactory")
                                            .isInstanceOf(TenantCommandSegmentFactory.class);
                         assertThat(context).getBean("tenantAxonServerQuerySegmentFactory")
                                            .isInstanceOf(TenantQuerySegmentFactory.class);
                         assertThat(context).getBean("tenantQueryUpdateEmitterSegmentFactory")
                                            .isInstanceOf(TenantQueryUpdateEmitterSegmentFactory.class);
                         assertThat(context).getBean("tenantEventSegmentFactory")
                                            .isInstanceOf(TenantEventSegmentFactory.class);
                         assertThat(context).getBean("tenantEventSchedulerSegmentFactory")
                                            .isInstanceOf(TenantEventSchedulerSegmentFactory.class);
                         assertThat(context).getBean("processorInfoConfiguration")
                                            .isInstanceOf(EventProcessorInfoConfiguration.class);
                         assertThat(context).getBean("tenantProvider")
                                            .isInstanceOf(AxonServerTenantProvider.class);
                     });
    }

    @Test
    void axonServerDisabled() {
        contextRunner.withPropertyValues("axon.axonserver.enabled:false", "axon.axonserver.contexts=tenant-1,tenant-2")
                     .run(context -> {
                         assertThat(context).doesNotHaveBean(TenantEventSchedulerSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(TenantCommandSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(TenantQuerySegmentFactory.class);
                         assertThat(context).doesNotHaveBean(TenantQueryUpdateEmitterSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(TenantEventSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(TenantEventSchedulerSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(TenantCommandSegmentFactory.class);
                         assertThat(context).doesNotHaveBean(EventProcessorInfoConfiguration.class);
                         assertThat(context).doesNotHaveBean(AxonServerTenantProvider.class);
                     });
    }
}