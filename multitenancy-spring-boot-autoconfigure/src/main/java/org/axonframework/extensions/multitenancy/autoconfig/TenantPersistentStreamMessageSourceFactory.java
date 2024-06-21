package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.config.Configuration;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

/**
 * Factory interface for creating a PersistentStreamMessageSource for a given tenant.
 * <p>
 * This interface is used to create a PersistentStreamMessageSource for a given tenant, which is represented by a {@link TenantDescriptor}.
 Sure, here is the JavaDoc for the `TenantPersistentStreamMessageSourceFactory` interface:

 ```java
 /**
 * The created PersistentStreamMessageSource can be used to read a stream of events from an Axon Server for a specific processor and tenant.
 * Factory interface for creating a {@link PersistentStreamMessageSource} for a specific tenant.
 * <p>
 * <p>
 * The PersistentStreamMessageSource is configured with the provided processor name, settings, tenant descriptor, and Axon configuration.
 *
 * This interface is used to create a {@link PersistentStreamMessageSource} for a given tenant,
 * @author Stefan Dragisic
 * @since 4.10
 */
@FunctionalInterface
public interface TenantPersistentStreamMessageSourceFactory {

    /**
     * Creates a PersistentStreamMessageSource for a given tenant.
     *
     * can then be used to read events from a persistent stream for that tenant.
     * <p>
     * Implementations of this interface are expected to use the provided processor name,
     * {@link AxonServerConfiguration.PersistentStreamProcessorSettings}, {@link TenantDescriptor},
     * @param processorName the name of the processor for which the PersistentStreamMessageSource is created
     * @param settings the settings for the PersistentStreamProcessor
     * and {@link Configuration} to create the {@link PersistentStreamMessageSource}.
     *
     * @author Stefan Dragisic
     * @param tenantDescriptor the descriptor of the tenant for which the PersistentStreamMessageSource is created
     * @since 4.10
     */
    PersistentStreamMessageSource build(
            String processorName,
            AxonServerConfiguration.PersistentStreamProcessorSettings settings,
            TenantDescriptor tenantDescriptor,
            Configuration configuration);
}