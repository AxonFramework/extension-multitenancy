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

/**
 * Spring Data JPA integration for multi-tenant applications.
 * <p>
 * This package provides support for using Spring Data JPA repositories in multi-tenant
 * Axon Framework applications. Key components include:
 * <ul>
 *     <li>{@link org.axonframework.extensions.multitenancy.spring.data.jpa.TenantDataSourceProvider} -
 *         Interface for providing tenant-specific DataSources</li>
 *     <li>{@link org.axonframework.extensions.multitenancy.spring.data.jpa.TenantEntityManagerFactoryBuilder} -
 *         Builder for creating tenant-specific EntityManagerFactories</li>
 *     <li>{@link org.axonframework.extensions.multitenancy.spring.data.jpa.TenantRepositoryFactory} -
 *         Factory for creating tenant-scoped Spring Data JPA repositories</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // 1. Implement TenantDataSourceProvider
 * TenantDataSourceProvider dataSourceProvider = tenant -> {
 *     return DataSourceBuilder.create()
 *         .url("jdbc:postgresql://localhost:5432/" + tenant.tenantId())
 *         .build();
 * };
 *
 * // 2. Build EntityManagerFactory per tenant
 * TenantEntityManagerFactoryBuilder emfBuilder = TenantEntityManagerFactoryBuilder
 *     .forDataSourceProvider(dataSourceProvider)
 *     .packagesToScan("com.example.domain")
 *     .build();
 *
 * // 3. Create repository factory
 * TenantRepositoryFactory<CustomerRepository> repoFactory =
 *     TenantRepositoryFactory.forRepository(CustomerRepository.class, emfBuilder);
 *
 * // 4. Register with multi-tenancy configurer
 * multiTenancyConfigurer.tenantComponent(CustomerRepository.class, repoFactory);
 *
 * // Now CustomerRepository can be injected in handlers and will be tenant-scoped
 * }</pre>
 *
 * @see org.axonframework.extensions.multitenancy.core.configuration.MultiTenancyConfigurer
 */
package org.axonframework.extensions.multitenancy.spring.data.jpa;
