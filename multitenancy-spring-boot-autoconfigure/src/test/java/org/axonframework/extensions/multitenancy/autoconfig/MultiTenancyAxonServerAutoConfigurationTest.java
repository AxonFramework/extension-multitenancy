/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSchedulerSegmentFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryUpdateEmitter;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQueryUpdateEmitterSegmentFactory;
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
import org.junit.jupiter.api.extension.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Stefan Dragisic
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource(properties = {"axon.axonserver.contexts=tenant-1,tenant-2"})
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
        contextRunner.withPropertyValues("axon.axonserver.enabled:false")
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