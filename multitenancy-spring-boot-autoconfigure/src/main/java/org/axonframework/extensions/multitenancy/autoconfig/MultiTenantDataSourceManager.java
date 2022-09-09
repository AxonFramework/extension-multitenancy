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

import org.axonframework.commandhandling.CommandBus;
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
 * @since 4.6.0
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

    /**
     * Registers a tenantDescriptor data source if the tenantDescriptor data source has not already been registered, using the {@link TenantDescriptor}
     * @param tenantDescriptor the descriptor of the tenant
     */
    public void register(TenantDescriptor tenantDescriptor) {
        if (tenantIsAbsent(tenantDescriptor)) {
            if (tenantDataSourceResolver != null) {
                DataSourceProperties dataSourceProperties;
                try {
                    dataSourceProperties = tenantDataSourceResolver.apply(tenantDescriptor);
                    log.debug("[d] Datasource properties resolved for tenantDescriptor ID '{}'", tenantDescriptor.tenantId());
                } catch (Exception e) {
                    throw new NoSuchTenantException("Could not resolve the tenantDescriptor!");
                }

                addTenant(tenantDescriptor, dataSourceProperties);
            }
        }

        log.debug("[d] Tenant '{}' set as current.", tenantDescriptor);
    }

    /**
     * Registers a dataSource for a tenantDescriptor and logs an error when an exception is thrown
     * @param tenantDescriptor the descriptor of the tenant
     * @param dataSourceProperties the properties for the datasource that needs to be registered
     */
    public void addTenant(TenantDescriptor tenantDescriptor, DataSourceProperties dataSourceProperties) {
        DataSource dataSource = DataSourceBuilder.create()
                                                 .driverClassName(dataSourceProperties.getDriverClassName())
                                                 .url(dataSourceProperties.getUrl())
                                                 .username(dataSourceProperties.getUsername())
                                                 .password(dataSourceProperties.getPassword())
                                                 .build();
        try (Connection c = dataSource.getConnection()) {
            tenantDataSources.put(tenantDescriptor, dataSource);
            multiTenantDataSource.afterPropertiesSet();
            log.debug("[d] Tenant '{}' added.", tenantDescriptor);
        } catch (SQLException t) {
            log.error("[d] Could not add tenantDescriptor '{}'", tenantDescriptor, t);
        }
    }

    /**
     * Unregisters the tenants dataSource
     * @param tenantDescriptor the descriptor of the tenant
     * @return the DataSource that was removed
     */
    public DataSource removeTenant(TenantDescriptor tenantDescriptor) {
        Object removedDataSource = tenantDataSources.remove(tenantDescriptor);
        multiTenantDataSource.afterPropertiesSet();
        return (DataSource) removedDataSource;
    }

    /**
     * Checks if the tenant datasource is not registered
     * @param tenantDescriptor
     * @return true is the tenant datasource is not registered
     */
    public boolean tenantIsAbsent(TenantDescriptor tenantDescriptor) {
        return !tenantDataSources.containsKey(tenantDescriptor);
    }

    private DataSource defaultDataSource() {
        DriverManagerDataSource defaultDataSource = new DriverManagerDataSource();
        defaultDataSource.setDriverClassName(properties.getDriverClassName());
        defaultDataSource.setUrl(properties.getUrl());
        defaultDataSource.setUsername(properties.getUsername());
        defaultDataSource.setPassword(properties.getPassword());
        return defaultDataSource;
    }

    /**
     * Registers the given {@code tenantDescriptor}.
     *
     * @param tenantDescriptor The tenantDescriptor to register
     * @return a Registration, which may be used to unregister the tenantDescriptor datasource
     */
    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        register(tenantDescriptor);
        return () -> removeTenant(tenantDescriptor) != null;
    }


    /**
     * Registers and starts the given {@code tenantDescriptor}.
     *
     * @param tenantDescriptor The tenantDescriptor to register
     * @return a Registration, which may be used to unregister the tenantDescriptor datasource
     */
    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }
}