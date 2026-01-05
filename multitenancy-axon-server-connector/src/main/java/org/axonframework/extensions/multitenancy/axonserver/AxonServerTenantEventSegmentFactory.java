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
package org.axonframework.extensions.multitenancy.axonserver;

import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngine;
import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngineFactory;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.extensions.multitenancy.eventsourcing.eventstore.TenantEventSegmentFactory;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A {@link TenantEventSegmentFactory} that creates {@link EventStore} instances
 * per tenant, connecting each to its own Axon Server context.
 * <p>
 * For each tenant, this factory:
 * <ul>
 *     <li>Creates an {@link AxonServerEventStorageEngine} for the tenant's context</li>
 *     <li>Wraps it in a {@link StorageEngineBackedEventStore}</li>
 * </ul>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantEventSegmentFactory
 * @see AxonServerEventStorageEngineFactory
 */
public class AxonServerTenantEventSegmentFactory implements TenantEventSegmentFactory {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerTenantEventSegmentFactory.class);

    private final Configuration configuration;
    private final TagResolver tagResolver;

    /**
     * Constructs an {@link AxonServerTenantEventSegmentFactory} using the provided configuration.
     *
     * @param configuration The Axon {@link Configuration} used to create tenant-specific event stores.
     * @param tagResolver   The {@link TagResolver} used for tagging events.
     */
    public AxonServerTenantEventSegmentFactory(
            @Nonnull Configuration configuration,
            @Nonnull TagResolver tagResolver
    ) {
        this.configuration = Objects.requireNonNull(configuration, "Configuration must not be null");
        this.tagResolver = Objects.requireNonNull(tagResolver, "TagResolver must not be null");
    }

    /**
     * Creates an {@link AxonServerTenantEventSegmentFactory} from the given {@link Configuration}.
     *
     * @param config The Axon {@link Configuration} to obtain components from.
     * @return A new factory instance, or {@code null} if required components are not available.
     */
    public static AxonServerTenantEventSegmentFactory createFrom(@Nonnull Configuration config) {
        // Only create if AxonServerConnectionManager is available
        if (!config.hasComponent(AxonServerConnectionManager.class)) {
            return null;
        }

        TagResolver tagResolver = config.getComponent(
                TagResolver.class,
                () -> new AnnotationBasedTagResolver()
        );

        return new AxonServerTenantEventSegmentFactory(config, tagResolver);
    }

    @Override
    public EventStore apply(TenantDescriptor tenant) {
        logger.debug("Creating EventStore segment for tenant [{}]", tenant.tenantId());

        // Create tenant-specific storage engine using the factory
        AxonServerEventStorageEngine storageEngine = AxonServerEventStorageEngineFactory.constructForContext(
                tenant.tenantId(),
                configuration
        );

        // Create event store with tenant-specific storage engine
        StorageEngineBackedEventStore eventStore = new StorageEngineBackedEventStore(
                storageEngine,
                new SimpleEventBus(),
                tagResolver
        );

        logger.debug("Created EventStore segment for tenant [{}]", tenant.tenantId());
        return eventStore;
    }
}
