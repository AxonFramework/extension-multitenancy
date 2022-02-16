package org.axonframework.extensions.multitenancy.components.eventstore;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Function;

/**
 * Factory for creating {@link EventStore} instances for a given {@link TenantDescriptor}.
 * <p>
 *
 * @author Stefan Dragisic
 */
public interface TenantEventSegmentFactory extends Function<TenantDescriptor, EventStore> {

    /**
     * Creates a new {@link EventStore} instance for the given {@link TenantDescriptor}.
     * @param tenantDescriptor the {@link TenantDescriptor} for which the {@link EventStore} should be created
     * @return the {@link EventStore} instance
     */
    default EventStore buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
