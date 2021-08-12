package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.queryhandling.QueryBus;

import java.util.function.Function;

/**
 * @author Stefan Dragisic
 */
public interface TenantQuerySegmentFactory extends Function<TenantDescriptor, QueryBus> {

    /**
     * @param tenantDescriptor
     * @return
     */
    default QueryBus buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
