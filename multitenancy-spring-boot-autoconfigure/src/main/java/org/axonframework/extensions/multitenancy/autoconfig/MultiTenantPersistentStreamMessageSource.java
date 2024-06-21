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

    private final AxonServerConfiguration.PersistentStreamProcessorSettings settings;

    private final Configuration configuration;
    private final TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory;
    private final Map<TenantDescriptor, PersistentStreamMessageSource> tenantSegments = new ConcurrentHashMap<>();

    public MultiTenantPersistentStreamMessageSource(Configuration configuration,
                                                    ScheduledExecutorService scheduledExecutorService,
                                                    String name,
                                                    AxonServerConfiguration.PersistentStreamProcessorSettings settings,
                                                    TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory) {

        super(name, configuration,
                new PersistentStreamProperties(settings.getName(),
                        settings.getInitialSegmentCount(),
                        settings.getSequencingPolicy(),
                        settings.getSequencingPolicyParameters(),
                        settings.getInitial(),
                        settings.getFilter()),
                scheduledExecutorService,
                settings.getBatchSize());
        this.tenantPersistentStreamMessageSourceFactory = tenantPersistentStreamMessageSourceFactory;
        this.settings = settings;
        this.name = name;
        this.configuration = configuration;
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        PersistentStreamMessageSource tenantSegment = tenantPersistentStreamMessageSourceFactory.build(name, settings, tenantDescriptor, configuration);
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