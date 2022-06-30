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
import org.axonframework.extensions.multitenancy.components.TenantProvider;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    public void testResolveDefaultDataSource() {
        DataSourceProperties dataSourceProperties = mock(DataSourceProperties.class);
        Function<TenantDescriptor, DataSourceProperties> tenantDataSourceResolver =
                (tenant) -> dataSourceProperties;

        DataSourceProperties defaultDataSourceProperties = mock(DataSourceProperties.class);
        when(defaultDataSourceProperties.getDriverClassName()).thenReturn(
                "org.springframework.jdbc.datasource.DriverManagerDataSource");
        when(defaultDataSourceProperties.getUrl()).thenReturn("default-url");
        when(defaultDataSourceProperties.getUsername()).thenReturn("default-username");
        when(defaultDataSourceProperties.getPassword()).thenReturn("default-password");

        DriverManagerDataSource expectedDataSource = new DriverManagerDataSource();
        expectedDataSource.setDriverClassName(defaultDataSourceProperties.getDriverClassName());
        expectedDataSource.setUrl(defaultDataSourceProperties.getUrl());
        expectedDataSource.setUsername(defaultDataSourceProperties.getUsername());
        expectedDataSource.setPassword(defaultDataSourceProperties.getPassword());

        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.subscribe(any())).thenReturn(() -> true);

        this.contextRunner
                .withAllowBeanDefinitionOverriding(true)
                .withBean(TenantProvider.class, () -> tenantProvider)
                .withBean("tenantDataSourceResolver", Function.class, () -> tenantDataSourceResolver)
                .withBean("properties", DataSourceProperties.class, () -> defaultDataSourceProperties)
                .run(context -> {
                    assertThat(context).getBean("multiTenantDataSourceManager")
                                       .returns(MultiTenantDataSourceManager.class, bean -> {
                                           MultiTenantDataSourceManager multiTenantDataSourceManager = ((MultiTenantDataSourceManager) bean);

                                           verify(tenantProvider).subscribe(multiTenantDataSourceManager);

                                           DriverManagerDataSource actualDataSource = (DriverManagerDataSource) ((MultiTenantDataSourceManager) bean).getMultiTenantDataSource()
                                                                                                                                                     .getResolvedDefaultDataSource();
                                           assertEquals(expectedDataSource.getUrl(), actualDataSource.getUrl());
                                           assertEquals(expectedDataSource.getUsername(),
                                                        actualDataSource.getUsername());
                                           assertEquals(expectedDataSource.getPassword(),
                                                        actualDataSource.getPassword());

                                           return MultiTenantDataSourceManager.class;
                                       });
                });
    }

    //todo complete
    //@Test
    public void testResolveTenantDataSource() {
        Function<TenantDescriptor, DataSourceProperties> tenantDataSourceResolver =
                (tenant) -> {
                    DataSourceProperties tenantDataSourceProperties = mock(DataSourceProperties.class);
                    when(tenantDataSourceProperties.getDriverClassName()).thenReturn(
                            "org.springframework.jdbc.datasource.DriverManagerDataSource");
                    when(tenantDataSourceProperties.getUrl()).thenReturn(tenant.tenantId() + "-url");
                    when(tenantDataSourceProperties.getUsername()).thenReturn(tenant.tenantId() + "-username");
                    when(tenantDataSourceProperties.getPassword()).thenReturn(tenant.tenantId() + "-password");
                    return tenantDataSourceProperties;
                };

        DataSourceProperties defaultDataSourceProperties = mock(DataSourceProperties.class);
        when(defaultDataSourceProperties.getDriverClassName()).thenReturn(
                "org.springframework.jdbc.datasource.DriverManagerDataSource");
        when(defaultDataSourceProperties.getUrl()).thenReturn("default-url");
        when(defaultDataSourceProperties.getUsername()).thenReturn("default-username");
        when(defaultDataSourceProperties.getPassword()).thenReturn("default-password");

        DriverManagerDataSource expectedDataSource = new DriverManagerDataSource();
        expectedDataSource.setDriverClassName(defaultDataSourceProperties.getDriverClassName());
        expectedDataSource.setUrl(defaultDataSourceProperties.getUrl());
        expectedDataSource.setUsername(defaultDataSourceProperties.getUsername());
        expectedDataSource.setPassword(defaultDataSourceProperties.getPassword());

        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.subscribe(any())).thenReturn(() -> true);

        this.contextRunner
                .withAllowBeanDefinitionOverriding(true)
                .withBean(TenantProvider.class, () -> tenantProvider)
                .withBean("tenantDataSourceResolver", Function.class, () -> tenantDataSourceResolver)
                .withBean("properties", DataSourceProperties.class, () -> defaultDataSourceProperties)
                .run(context -> {
                    assertThat(context).getBean("multiTenantDataSourceManager")
                                       .returns(MultiTenantDataSourceManager.class, bean -> {
                                           MultiTenantDataSourceManager multiTenantDataSourceManager = ((MultiTenantDataSourceManager) bean);

                                           verify(tenantProvider).subscribe(multiTenantDataSourceManager);

                                           multiTenantDataSourceManager.registerTenant(TenantDescriptor.tenantWithId(
                                                   "tenant-1"));
                                           multiTenantDataSourceManager.registerAndStartTenant(TenantDescriptor.tenantWithId(
                                                   "tenant-2"));
                                           multiTenantDataSourceManager.removeTenant(TenantDescriptor.tenantWithId(
                                                   "tenant-1"));

                                           DriverManagerDataSource actualDataSource = (DriverManagerDataSource) ((MultiTenantDataSourceManager) bean).getMultiTenantDataSource()
                                                                                                                                                     .getResolvedDefaultDataSource();
                                           assertEquals(expectedDataSource.getUrl(), actualDataSource.getUrl());
                                           assertEquals(expectedDataSource.getUsername(),
                                                        actualDataSource.getUsername());
                                           assertEquals(expectedDataSource.getPassword(),
                                                        actualDataSource.getPassword());


                                           return MultiTenantDataSourceManager.class;
                                       });
                });
    }
}