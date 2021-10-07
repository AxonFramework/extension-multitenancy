package org.axonframework.extensions.multitenancy.eventhandeling;

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.extensions.multitenancy.commandbus.TenantDescriptor;

import java.util.function.Function;

/**
 * @author Stefan Dragisic
 */

public interface TenantEventProcessorSegmentFactory extends Function<TenantDescriptor, EventProcessor> {

    /**
     * @param tenantDescriptor
     * @return
     */
    default EventProcessor buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
