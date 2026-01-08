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
package org.axonframework.extension.multitenancy.integrationtests.springboot.embedded;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Spring Boot test application for multi-tenancy integration tests.
 * Uses auto-discovered handlers and tenant-scoped Spring Data JPA repositories.
 * <p>
 * When multi-tenancy is enabled (default), the {@code MultiTenancyAutoConfigurationImportFilter}
 * automatically excludes conflicting autoconfiguration classes:
 * <ul>
 *     <li>InfrastructureConfiguration - replaced by MultiTenantMessageHandlerLookup</li>
 *     <li>JpaAutoConfiguration - replaced by TenantTokenStoreFactory</li>
 *     <li>JpaEventStoreAutoConfiguration - replaced by MultiTenantEventStore via SPI</li>
 *     <li>HibernateJpaAutoConfiguration - excluded when TenantDataSourceProvider is present</li>
 *     <li>JpaRepositoriesAutoConfiguration - excluded when TenantDataSourceProvider is present</li>
 * </ul>
 */
@SpringBootApplication
public class TestApplication {

    /**
     * Provides a Clock bean to demonstrate that TenantComponent implementations
     * can receive Spring dependencies via constructor injection.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
