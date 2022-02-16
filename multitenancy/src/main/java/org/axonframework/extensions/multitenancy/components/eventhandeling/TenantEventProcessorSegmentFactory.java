package org.axonframework.extensions.multitenancy.components.eventhandeling;

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Function;

/**
 * Factory for creating {@link EventProcessor} segments for a given {@link TenantDescriptor}.
 * <p>
 *
 * @author Stefan Dragisic
 */

public interface TenantEventProcessorSegmentFactory extends Function<TenantDescriptor, EventProcessor> {

    /**
     * Creates a new {@link EventProcessor} segment for the given {@link TenantDescriptor}.
     * @param tenantDescriptor the {@link TenantDescriptor} for which to create a new {@link EventProcessor} segment
     * @return a new {@link EventProcessor} segment for the given {@link TenantDescriptor}
     */
    default EventProcessor buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
