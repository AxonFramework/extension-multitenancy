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

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.springboot.autoconfig.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * Test class validating Multi-Tenancy auto-configuration for the {@code DataSourceManager}.
 *
 * @author Stefan Dragisic
 */
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
                            MultiTenantDataSourceManager.class,
                            AxonTracingAutoConfiguration.class
                    ));

    @Test
    void resolveDefaultDataSourceProperties() {
        DataSourceProperties dataSourceProperties = mock(DataSourceProperties.class);
        Function<TenantDescriptor, DataSourceProperties> tenantDataSourceResolver =
                (tenant) -> dataSourceProperties;

        DataSourceProperties defaultDataSourceProperties = mock(DataSourceProperties.class);

        // Create a mock DataSource
        DataSource mockDataSource = mock(DataSource.class);

        // Mock the initializeDataSourceBuilder() to return a builder that returns our mockDataSource
        DataSourceBuilder mockBuilder = mock(DataSourceBuilder.class);
        when(mockBuilder.build()).thenReturn(mockDataSource);
        when(defaultDataSourceProperties.initializeDataSourceBuilder()).thenReturn(mockBuilder);

        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.subscribe(any())).thenReturn(() -> true);

        this.contextRunner.withPropertyValues("axon.axonserver.contexts=default")
                .withAllowBeanDefinitionOverriding(true)
                .withBean(TenantProvider.class, () -> tenantProvider)
                .withBean("tenantDataSourceResolver", Function.class, () -> tenantDataSourceResolver)
                .withBean("properties", DataSourceProperties.class, () -> defaultDataSourceProperties)
                .run(context -> {
                    assertThat(context).hasSingleBean(MultiTenantDataSourceManager.class);
                    MultiTenantDataSourceManager multiTenantDataSourceManager = context.getBean(MultiTenantDataSourceManager.class);

                    verify(tenantProvider).subscribe(multiTenantDataSourceManager);

                    DataSource actualDataSource = multiTenantDataSourceManager.getMultiTenantDataSource()
                            .getResolvedDefaultDataSource();

                    assertSame(mockDataSource, actualDataSource);
                });
    }

    @Test
    void resolveTenantDataSource() {
        DataSource mockDataSource = mock(DataSource.class);
        AtomicBoolean dataSourceResolved = new AtomicBoolean(false);
        Function<TenantDescriptor, DataSource> tenantDataSourceResolver =
                (tenant) -> {
            dataSourceResolved.set(true);
            return mockDataSource;

                };

        DataSourceProperties defaultDataSourceProperties = mock(DataSourceProperties.class);
        DataSourceBuilder mockBuilder = mock(DataSourceBuilder.class);
        when(mockBuilder.build()).thenReturn(mockDataSource);
        when(defaultDataSourceProperties.initializeDataSourceBuilder()).thenReturn(mockBuilder);

        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.subscribe(any())).thenReturn(() -> true);

        this.contextRunner.withPropertyValues("axon.axonserver.contexts=default")
                .withAllowBeanDefinitionOverriding(true)
                .withBean(TenantProvider.class, () -> tenantProvider)
                .withBean("tenantDataSourceResolver", Function.class, () -> tenantDataSourceResolver)
                .withBean("tenantDataSourceResolver", Function.class, () -> tenantDataSourceResolver)
                .withBean("properties", DataSourceProperties.class, () -> defaultDataSourceProperties)
                .run(context -> {
                    assertThat(context).hasSingleBean(MultiTenantDataSourceManager.class);
                    MultiTenantDataSourceManager multiTenantDataSourceManager = context.getBean(MultiTenantDataSourceManager.class);
                    verify(tenantProvider).subscribe(multiTenantDataSourceManager);
                   multiTenantDataSourceManager.registerTenant(TenantDescriptor.tenantWithId("test"));
                   assertThat(dataSourceResolved.get()).isTrue();
                });
    }

}