package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.queryhandling.QueryBus;

import java.util.function.Function;

/**
 * Factory for creating {@link QueryBus} instances for a given {@link TenantDescriptor}.
 *
 * @author Stefan Dragisic
 */
public interface TenantQuerySegmentFactory extends Function<TenantDescriptor, QueryBus> {

    /**
     * Creates a new {@link QueryBus} instance for the given {@link TenantDescriptor}.
     * @param tenantDescriptor the {@link TenantDescriptor} for which the {@link QueryBus} should be created
     * @return the {@link QueryBus} instance
     */
    default QueryBus buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
