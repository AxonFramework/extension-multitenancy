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

import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.sql.DataSource;

/**
 * Configuration for the MultiTenantDataSourceManager. Works in conjunction with the {@link
 * TenantWrappedTransactionManager} which is used to add tenant to transaction context.
 * <p>
 * Used for multi-tenant JPA support.
 * <p>
 *
 * @author Stefan Dragisic
 */

@Configuration
@ConditionalOnProperty(value = "axon.multi-tenancy.enabled", matchIfMissing = true)
@ConditionalOnBean(name = "tenantDataSourceResolver")
public class MultiTenantDataSourceManager implements MultiTenantAwareComponent {

    private static final Logger log = LoggerFactory.getLogger(MultiTenantDataSourceManager.class);

    private final Map<TenantDescriptor, Object> tenantDataSources = new ConcurrentHashMap<>();

    private final DataSourceProperties properties;

    private final Function<TenantDescriptor, DataSourceProperties> tenantDataSourceResolver;

    private AbstractRoutingDataSource multiTenantDataSource;

    private final TargetTenantResolver<Message<?>> tenantResolver;

    public MultiTenantDataSourceManager(DataSourceProperties properties,
                                        TargetTenantResolver<Message<?>> tenantResolver,
                                        @Autowired(required = false)
                                                Function<TenantDescriptor, DataSourceProperties> tenantDataSourceResolver) {
        this.properties = properties;
        this.tenantResolver = tenantResolver;
        this.tenantDataSourceResolver = tenantDataSourceResolver;
    }

    /**
     * Dynamically chooses a DataSource for the given tenant, based on message from {@link
     * org.axonframework.messaging.unitofwork.UnitOfWork} or from transaction provided by {@link
     * TenantWrappedTransactionManager}
     *
     * @param tenantProvider
     * @return
     */
    @Primary
    @Bean
    public DataSource tenantDataSource(TenantProvider tenantProvider) {
        multiTenantDataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                if (!CurrentUnitOfWork.isStarted()) {
                    return TenantWrappedTransactionManager.getCurrentTenant();
                }
                Message<?> message = CurrentUnitOfWork.get().getMessage();
                return tenantResolver.resolveTenant(message, tenantDataSources.keySet()).tenantId();
            }
        };
        multiTenantDataSource.setTargetDataSources(Collections.unmodifiableMap(tenantDataSources));
        DataSource defaultDatasource = defaultDataSource();
        multiTenantDataSource.setDefaultTargetDataSource(defaultDatasource);
        multiTenantDataSource.afterPropertiesSet();

        tenantProvider.subscribe(this);

        return multiTenantDataSource;
    }

    private void register(TenantDescriptor tenant) {
        if (tenantIsAbsent(tenant)) {
            if (tenantDataSourceResolver != null) {
                DataSourceProperties dataSourceProperties;
                try {
                    dataSourceProperties = tenantDataSourceResolver.apply(tenant);
                    log.debug("[d] Datasource properties resolved for tenant ID '{}'", tenant);
                } catch (Exception e) {
                    throw new NoSuchTenantException("Could not resolve the tenant!");
                }

                addTenant(tenant, dataSourceProperties);
            }
        }

        log.debug("[d] Tenant '{}' set as current.", tenant);
    }

    private void addTenant(TenantDescriptor tenant, DataSourceProperties dataSourceProperties) {
        DataSource dataSource = DataSourceBuilder.create()
                                                 .driverClassName(dataSourceProperties.getDriverClassName())
                                                 .url(dataSourceProperties.getUrl())
                                                 .username(dataSourceProperties.getUsername())
                                                 .password(dataSourceProperties.getPassword())
                                                 .build();
        try (Connection ignored = dataSource.getConnection()) {
            tenantDataSources.put(tenant, dataSource);
            multiTenantDataSource.afterPropertiesSet();
            log.debug("[d] Tenant '{}' added.", tenant);
        } catch (SQLException t) {
            log.error("[d] Could not add tenant '{}'", tenant, t);
        }
    }

    public DataSource removeTenant(TenantDescriptor tenant) {
        Object removedDataSource = tenantDataSources.remove(tenant);
        multiTenantDataSource.afterPropertiesSet();
        return (DataSource) removedDataSource;
    }

    public boolean tenantIsAbsent(TenantDescriptor tenant) {
        return !tenantDataSources.containsKey(tenant);
    }

    public AbstractRoutingDataSource getMultiTenantDataSource() {
        return multiTenantDataSource;
    }

    private DataSource defaultDataSource() {
        DriverManagerDataSource defaultDataSource = new DriverManagerDataSource();
        defaultDataSource.setDriverClassName(properties.getDriverClassName());
        defaultDataSource.setUrl(properties.getUrl());
        defaultDataSource.setUsername(properties.getUsername());
        defaultDataSource.setPassword(properties.getPassword());
        return defaultDataSource;
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        register(tenantDescriptor);
        return () -> removeTenant(tenantDescriptor) != null;
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }
}