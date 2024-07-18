package org.axonframework.extensions.multitenancy.autoconfig;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.config.Configuration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.MultiTenantSubscribableMessageSource;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.common.Registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class MultiTenantPersistentStreamMessageSource extends PersistentStreamMessageSource
        implements MultiTenantAwareComponent, MultiTenantSubscribableMessageSource<PersistentStreamMessageSource> {

    private final String name;

    private final Configuration configuration;
    private final TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory;
    private final Map<TenantDescriptor, PersistentStreamMessageSource> tenantSegments = new ConcurrentHashMap<>();


    private final PersistentStreamProperties persistentStreamProperties;
    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final String context;

    public MultiTenantPersistentStreamMessageSource(String name, PersistentStreamProperties
            persistentStreamProperties, ScheduledExecutorService scheduler, int batchSize, String context, Configuration configuration,
                                                    TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory) {

        super(name, configuration, persistentStreamProperties, scheduler, batchSize, context);
        this.tenantPersistentStreamMessageSourceFactory = tenantPersistentStreamMessageSourceFactory;
        this.name = name;
        this.configuration = configuration;
        this.persistentStreamProperties = persistentStreamProperties;
        this.scheduler = scheduler;
        this.batchSize = batchSize;
        this.context = context;
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        PersistentStreamMessageSource tenantSegment = tenantPersistentStreamMessageSourceFactory.build( name,
                persistentStreamProperties,  scheduler,  batchSize,  context, configuration, tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            PersistentStreamMessageSource removed = tenantSegments.remove(tenantDescriptor);
            return removed != null;
        };
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }

    @Override
    public Map<TenantDescriptor, PersistentStreamMessageSource> tenantSegments() {
        return tenantSegments;
    }
}