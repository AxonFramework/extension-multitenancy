package org.axonframework.extensions.multitenancy.components;

import java.util.Map;

/**
 * Interface for multi-tenant message sources that can provide tenant segments.
 *
 * @param <T> The type of the tenant segment
 */
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;

/**
 * Interface for multi-tenant message sources that can provide tenant segments.
 *
 * @param <T> The type of the tenant segment, which must extend MessageSource
 */
public interface MultiTenantSubscribableMessageSource<T extends SubscribableMessageSource<EventMessage<?>>> {

    /**
     * Returns a map of tenant segments, where the key is the TenantDescriptor
     * and the value is the corresponding tenant segment of type T, which extends MessageSource.
     *
     * @return A map of TenantDescriptor to tenant segments
     */
    Map<TenantDescriptor, T> tenantSegments();
}
