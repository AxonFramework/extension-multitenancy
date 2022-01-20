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
 *
 * @author Stefan Dragisic
 */

@Configuration
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

    public void register(TenantDescriptor tenant) {
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

    public void addTenant(TenantDescriptor tenant, DataSourceProperties dataSourceProperties) {
        DataSource dataSource = DataSourceBuilder.create()
                                                 .driverClassName(dataSourceProperties.getDriverClassName())
                                                 .url(dataSourceProperties.getUrl())
                                                 .username(dataSourceProperties.getUsername())
                                                 .password(dataSourceProperties.getPassword())
                                                 .build();

        // Check that new connection is 'live'. If not - throw exception
        try (Connection c = dataSource.getConnection()) {
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