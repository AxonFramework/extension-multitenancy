package org.axonframework.extensions.multitenancy.components.eventstore;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Function;

/**
 * @author Stefan Dragisic
 */
public interface TenantEventSegmentFactory extends Function<TenantDescriptor, EventStore> {

    /**
     * @param tenantDescriptor
     * @return
     */
    default EventStore buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
