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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                    assertThat(TestConfiguration.dataSourceResolved.get());
                });
    }

    @Test
    void resolveTenantDataSource() {
        DataSourceProperties defaultDataSourceProperties = mock(DataSourceProperties.class);

        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.subscribe(any())).thenReturn(() -> true);

        this.contextRunner
                .withPropertyValues("axon.axonserver.contexts=default")
                .withAllowBeanDefinitionOverriding(true)
               // .withUserConfiguration(DataSourceResolverConfiguration.class)
                .withBean(TenantProvider.class, () -> tenantProvider)
                .withBean("properties", DataSourceProperties.class, () -> defaultDataSourceProperties)
                .run(context -> {
                    assertThat(context).hasSingleBean(MultiTenantDataSourceManager.class);
                    MultiTenantDataSourceManager multiTenantDataSourceManager = context.getBean(MultiTenantDataSourceManager.class);
                    verify(tenantProvider).subscribe(multiTenantDataSourceManager);
                    multiTenantDataSourceManager.registerTenant(TenantDescriptor.tenantWithId("test"));
                    assertThat(DataSourceResolverConfiguration.dataSourceResolved.get()).isTrue();
                });
    }

    @Configuration
    static class TestConfiguration {
        public static AtomicBoolean dataSourceResolved = new AtomicBoolean(false);
        @Bean
        public Function<DataSourceProperties, DataSource> dataSourceBuilder() {
            return properties -> {
                dataSourceResolved.set(true);
                return mock(DataSource.class);
            };
        }
    }

    @Configuration
    static class  DataSourceResolverConfiguration {

        public static AtomicBoolean dataSourceResolved = new AtomicBoolean(false);
        @Bean
        public Function<TenantDescriptor, DataSource> tenantDataSourceResolver() {
            return tenant -> {
                dataSourceResolved.set(true);
                return mock(DataSource.class);
            };
        }
    }

}