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

import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.*;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Autoconfiguration for the MultiTenantDataSourceManager. Works in conjunction with the
 * {@link TenantWrappedTransactionManager} that is used to add tenant to transaction context.
 * <p>
 * Provides multi-tenant support for JPA-based applications.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
@Configuration
@ConditionalOnProperty(value = "axon.multi-tenancy.enabled", matchIfMissing = true)
@ConditionalOnBean(name = "tenantDataSourceResolver")
public class MultiTenantDataSourceManager implements MultiTenantAwareComponent {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantDataSourceManager.class);

    private final Map<TenantDescriptor, Object> tenantDataSources = new ConcurrentHashMap<>();
    private AbstractRoutingDataSource multiTenantDataSource;

    private final DataSourceProperties properties;
    private final TargetTenantResolver<Message<?>> tenantResolver;
    private final Function<TenantDescriptor, DataSourceProperties> dataSourcePropertyResolver;

    private final Function<TenantDescriptor, DataSource> dataSourceResolver;


    /**
     * Constructs a {@link MultiTenantDataSourceManager}.
     *
     * @param properties                 The default {@link DataSourceProperties} for the
     *                                   {@link AbstractRoutingDataSource tenant-aware DataSource}.
     * @param tenantResolver             A lambda used to resolve a {@link TenantDescriptor tenant} based on a
     *                                   {@link UnitOfWork#getMessage() message}. Integral part of the tenant-aware
     *                                   {@link DataSource} constructed by this class.
     * @param dataSourcePropertyResolver A lambda resolving the tenant-specific {@link DataSourceProperties} based on a
     *                                   given {@link TenantDescriptor tenant}.
     */
    public MultiTenantDataSourceManager(DataSourceProperties properties,
                                        TargetTenantResolver<Message<?>> tenantResolver,
                                        @Autowired(required = false)
                                        Function<TenantDescriptor, DataSourceProperties> dataSourcePropertyResolver,
                                        @Autowired(required = false)
                                        Function<TenantDescriptor, DataSource> dataSourceResolver) {
        this.properties = properties;
        this.tenantResolver = tenantResolver;
        this.dataSourcePropertyResolver = dataSourcePropertyResolver;
        this.dataSourceResolver = dataSourceResolver;
    }

    /**
     * Bean creation method for a {@link DataSource} implementation that dynamically chooses a tenant-specific
     * {@code DataSource}. Does so through {@link UnitOfWork#getMessage() message} from the
     * {@link org.axonframework.messaging.unitofwork.UnitOfWork}, or from transaction provided by
     * {@link TenantWrappedTransactionManager}.
     *
     * @param tenantProvider The {@link TenantProvider} to register the {@link MultiTenantDataSourceManager} with.
     * @return A {@link DataSource} implementation that dynamically chooses a tenant-specific
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
                return tenantResolver.resolveTenant(message, tenantDataSources.keySet());
            }
        };
        multiTenantDataSource.setTargetDataSources(Collections.unmodifiableMap(tenantDataSources));
        DataSource defaultDatasource = defaultDataSource();
        multiTenantDataSource.setDefaultTargetDataSource(defaultDatasource);
        multiTenantDataSource.setLenientFallback(false);
        multiTenantDataSource.afterPropertiesSet();

        tenantProvider.subscribe(this);
        return multiTenantDataSource;
    }

    /**
     * Creates and configures a default DataSource using the properties set in the application.
     *
     * This method initializes a DriverManagerDataSource with the following configurations:
     * - Driver class name
     * - Database URL
     * - Username
     * - Password
     *
     * These configurations are obtained from the application properties.
     *
     * @return A configured DriverManagerDataSource to be used as the default DataSource.
     * @throws IllegalStateException if any of the required properties are not set.
     */
    protected DataSource defaultDataSource() {
        return properties.initializeDataSourceBuilder().build();
    }

    private boolean tenantIsAbsent(TenantDescriptor tenantDescriptor) {
        return !tenantDataSources.containsKey(tenantDescriptor);
    }

    AbstractRoutingDataSource getMultiTenantDataSource() {
        return multiTenantDataSource;
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
        return () -> unregister(tenantDescriptor) != null;
    }

    private void register(TenantDescriptor tenant) {
        if (tenantIsAbsent(tenant)) {
            if (dataSourceResolver != null) {
                DataSource dataSource;
                try {
                    dataSource = dataSourceResolver.apply(tenant);
                    logger.debug("[d] Datasource properties resolved for tenant descriptor [{}]", tenant);
                } catch (Exception e) {
                    throw new NoSuchTenantException("Could not resolve the tenant!");
                }
                addTenant(tenant, dataSource);
            }
            else if (dataSourcePropertyResolver != null) {
                DataSourceProperties dataSourceProperties;
                try {
                    dataSourceProperties = dataSourcePropertyResolver.apply(tenant);
                    logger.debug("[d] Datasource properties resolved for tenant descriptor [{}]", tenant);
                } catch (Exception e) {
                    throw new NoSuchTenantException("Could not resolve the tenant!");
                }
                addTenant(tenant, dataSourceProperties);
            }
        }
        logger.debug("[d] Tenant [{}] set as current.", tenant);
    }

    /**
     * Adds a new tenant to the system using the provided tenant descriptor and data source properties.
     * This method creates a new DataSource from the properties and then adds it to the system.
     *
     * @param tenant The descriptor of the tenant to be added.
     * @param dataSourceProperties The properties used to create the DataSource for this tenant.
     */
    protected void addTenant(TenantDescriptor tenant, DataSourceProperties dataSourceProperties) {
        DataSource dataSource = dataSourceProperties
                .initializeDataSourceBuilder()
                .build();
        addTenant(tenant, dataSource);
    }

    /**
     * Adds a new tenant to the system using the provided tenant descriptor and pre-configured DataSource.
     * This method validates the DataSource, adds it to the tenant map, and performs necessary setup.
     *
     * @param tenant The descriptor of the tenant to be added.
     * @param dataSource The pre-configured DataSource for this tenant.
     */
    protected void addTenant(TenantDescriptor tenant, DataSource dataSource) {
        try (Connection ignored = dataSource.getConnection()) {
            tenantDataSources.put(tenant, dataSource);
            multiTenantDataSource.afterPropertiesSet();
            logger.debug("[d] Tenant '{}' added.", tenant);
        } catch (SQLException t) {
            logger.error("[d] Could not add tenant '{}'", tenant, t);
        }
    }

    private DataSource unregister(TenantDescriptor tenantDescriptor) {
        Object removedDataSource = tenantDataSources.remove(tenantDescriptor);
        multiTenantDataSource.afterPropertiesSet();
        return (DataSource) removedDataSource;
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