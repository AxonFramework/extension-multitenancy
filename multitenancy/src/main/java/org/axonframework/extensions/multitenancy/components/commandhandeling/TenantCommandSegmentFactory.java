package org.axonframework.extensions.multitenancy.components.commandhandeling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Function;

/**
 * @author Stefan Dragisic
 */
public interface TenantCommandSegmentFactory extends Function<TenantDescriptor, CommandBus> {

    /**
     * @param tenantDescriptor
     * @return
     */
    default CommandBus buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
