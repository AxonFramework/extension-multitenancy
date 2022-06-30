/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
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
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource(properties = {"axon.axonserver.contexts=default"})
class MultiTenantDataSourceManagerTest {

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
                            MultiTenancyAxonServerAutoConfiguration.class,
                            MultiTenancyAutoConfiguration.class,
                            MultiTenantDataSourceManager.class
                    ));

    @BeforeEach
    void setUp() {
    }

    @Test
    public void test() {
        DataSourceProperties dataSourceProperties = mock(DataSourceProperties.class);
        Function<TenantDescriptor, DataSourceProperties> tenantDataSourceResolver =
                (tenant) -> dataSourceProperties;

        DataSourceProperties properties = mock(DataSourceProperties.class);
        when(properties.getDriverClassName()).thenReturn("org.springframework.jdbc.datasource.DriverManagerDataSource");
        when(properties.getUrl()).thenReturn("url");
        when(properties.getUrl()).thenReturn("userName");
        when(properties.getUrl()).thenReturn("password");

        this.contextRunner
                .withBean("tenantDataSourceResolver", Function.class, () -> tenantDataSourceResolver)
                .withBean("properties", DataSourceProperties.class, () -> properties)
                .run(context -> {
                    assertThat(context).getBean("multiTenantDataSourceManager")
                                       .returns(MultiTenantDataSourceManager.class, bean -> {
                                           MultiTenantDataSourceManager multiTenantDataSourceManager = ((MultiTenantDataSourceManager) bean);

                                           //verify tenantProvider.subscriber
                                           //test CurrentUnitOfWork is started
                                           return MultiTenantDataSourceManager.class;
                                       });
                });
    }

    //with bean tenantDataSourceResolver
}