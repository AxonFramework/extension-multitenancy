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
package org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.config;

import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.core.TenantProvider;
import org.axonframework.extension.multitenancy.core.SimpleTenantProvider;
import org.axonframework.extension.multitenancy.spring.data.jpa.TenantDataSourceProvider;
import org.axonframework.extension.multitenancy.spring.data.jpa.TenantEntityManagerFactoryBuilder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

/**
 * Configuration for multi-tenancy tests.
 * Provides tenant definitions and H2 database-per-tenant via {@link TenantDataSourceProvider}.
 */
@Configuration
public class TestMultiTenancyConfiguration {

    public static final TenantDescriptor TENANT_A = TenantDescriptor.tenantWithId("tenant-a");
    public static final TenantDescriptor TENANT_B = TenantDescriptor.tenantWithId("tenant-b");

    @Bean
    public TenantProvider tenantProvider() {
        return new SimpleTenantProvider(List.of(TENANT_A, TENANT_B));
    }

    @Bean
    public TenantDataSourceProvider tenantDataSourceProvider() {
        Map<String, DataSource> cache = new ConcurrentHashMap<>();
        return tenant -> cache.computeIfAbsent(tenant.tenantId(), id ->
                DataSourceBuilder.create()
                        .url("jdbc:h2:mem:" + id + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                        .driverClassName("org.h2.Driver")
                        .username("sa")
                        .password("")
                        .build()
        );
    }

    /**
     * Configures the TenantEntityManagerFactoryBuilder with entity packages and DDL auto.
     * This ensures tenant databases have the schema created and entity classes are scanned.
     */
    @Bean
    public TenantEntityManagerFactoryBuilder tenantEntityManagerFactoryBuilder(
            TenantDataSourceProvider dataSourceProvider) {
        return TenantEntityManagerFactoryBuilder
                .forDataSourceProvider(dataSourceProvider)
                .packagesToScan("org.axonframework.extensions.multitenancy.integrationtests.springboot.embedded.domain.read.coursestats")
                .jpaProperty("hibernate.hbm2ddl.auto", "create-drop")
                .jpaProperty("hibernate.show_sql", "true")
                .build();
    }
}
