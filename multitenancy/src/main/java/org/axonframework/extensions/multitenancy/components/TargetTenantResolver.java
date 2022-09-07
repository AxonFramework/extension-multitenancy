package org.axonframework.extensions.multitenancy.components;

import org.axonframework.messaging.Message;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

/**
 * Resolves the target tenant of a given message.
 * <p>
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface TargetTenantResolver<M extends Message<?>> extends BiFunction<M, Collection<TenantDescriptor>, TenantDescriptor> {

    /**
     * Returns {@link TenantDescriptor} for the given message.
     * @param message the message to resolve the target tenant for
     * @param tenants the collection of tenants to resolve the target tenant from (may be empty)
     * @return resolved {@link TenantDescriptor}
     */
    default TenantDescriptor resolveTenant(M message, Collection<TenantDescriptor> tenants) {
        return this.apply(message, Collections.unmodifiableCollection(tenants));
    }
}
