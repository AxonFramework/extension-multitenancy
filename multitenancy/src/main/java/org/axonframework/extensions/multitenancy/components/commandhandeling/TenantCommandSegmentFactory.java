package org.axonframework.extensions.multitenancy.components.commandhandeling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Function;

/**
 * Factory for creating {@link CommandBus} instances that are configured to only receive commands for a specific
 * tenant.
 * <p>
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface TenantCommandSegmentFactory extends Function<TenantDescriptor, CommandBus> {

    /**
     * Creates a new {@link CommandBus} instance that is configured to only receive commands for the given {@link TenantDescriptor}.
     * @param tenantDescriptor the {@link TenantDescriptor} for which the {@link CommandBus} should be created
     * @return the {@link CommandBus} instance
     */
    default CommandBus buildTenantSegment(TenantDescriptor tenantDescriptor) {
        return apply(tenantDescriptor);
    }
}
