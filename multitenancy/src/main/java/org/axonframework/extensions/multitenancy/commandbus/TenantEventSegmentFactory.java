package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.eventhandling.EventBus;

import java.util.function.Function;

/**
 * @author Stefan Dragisic
 */
public interface TenantEventSegmentFactory extends Function<TenantDescriptor, EventBus> {

    /**
     * @param tenantDescriptor
     * @return
     */
    default EventBus buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
