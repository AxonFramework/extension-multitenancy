/*
 * Copyright (c) 2010-2024. Axon Framework
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
package org.axonframework.extensions.multitenancy.components.eventstore;


import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.config.Configuration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.common.Registration;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A multi-tenant persistent stream message source that extends PersistentStreamMessageSource
 * and implements MultiTenantAwareComponent and MultiTenantSubscribableMessageSource interfaces.
 * <p>
 * This class provides functionality to manage message sources for multiple tenants,
 * allowing registration and management of tenant-specific persistent stream message sources.
 * It maintains a concurrent map of tenant descriptors to their corresponding message sources.
 * </p>
 * <p>
 * The class supports operations such as registering new tenants, starting tenants,
 * and retrieving all tenant segments. It uses a factory to create tenant-specific
 * message sources, ensuring proper initialization and configuration for each tenant.
 * </p>
 * @author Stefan Dragisic
 * @since 4.10.0
 */
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

    /**
     * Constructs a new MultiTenantPersistentStreamMessageSource.
     *
     * @param name The name of the message source.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler The scheduled executor service for managing tasks.
     * @param batchSize The size of each batch of messages to process.
     * @param context The context in which this message source operates.
     * @param configuration The configuration settings for the message source.
     * @param tenantPersistentStreamMessageSourceFactory The factory for creating tenant-specific message sources.
     */
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

    /**
     * Registers a new tenant with the message source.
     *
     * @param tenantDescriptor The descriptor of the tenant to register.
     * @return A Registration object that can be used to unregister the tenant.
     */
    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        PersistentStreamMessageSource tenantSegment = tenantPersistentStreamMessageSourceFactory.build(name,
                persistentStreamProperties, scheduler, batchSize, context, configuration, tenantDescriptor);
        tenantSegments.putIfAbsent(tenantDescriptor, tenantSegment);

        return () -> {
            PersistentStreamMessageSource removed = tenantSegments.remove(tenantDescriptor);
            return removed != null;
        };
    }

    /**
     * Registers and starts a new tenant with the message source.
     * In this implementation, it's equivalent to just registering the tenant.
     * This component doesn't require any additional steps to start a tenant.
     *
     * @param tenantDescriptor The descriptor of the tenant to register and start.
     * @return A Registration object that can be used to unregister the tenant.
     */
    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }

    /**
     * Returns a map of all registered tenant segments.
     *
     * @return An unmodifiable map where keys are TenantDescriptors and values are PersistentStreamMessageSources.
     */
    @Override
    public Map<TenantDescriptor, PersistentStreamMessageSource> tenantSegments() {
        return Collections.unmodifiableMap(tenantSegments);
    }
}