package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.commandhandling.CommandBus;

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
