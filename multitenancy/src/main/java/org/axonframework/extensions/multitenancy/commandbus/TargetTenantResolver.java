package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.messaging.Message;

import java.util.Collection;
import java.util.function.BiFunction;

/**
 * @author Stefan Dragisic
 */
public interface TargetTenantResolver<M extends Message<?>> extends BiFunction<M, Collection<TenantDescriptor>, TenantDescriptor> {
//todo use imutable interface

    /**
     * @param message
     * @return
     */
    default TenantDescriptor resolveTenant(M message, Collection<TenantDescriptor> tenants) {
        return this.apply(message, tenants);
    }
}
