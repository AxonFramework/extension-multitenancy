package org.axonframework.extensions.multitenancy.components;

import org.axonframework.messaging.Message;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

/**
 * @author Stefan Dragisic
 */
public interface TargetTenantResolver<M extends Message<?>> extends BiFunction<M, Collection<TenantDescriptor>, TenantDescriptor> {

    /**
     * @param message
     * @return
     */
    default TenantDescriptor resolveTenant(M message, Collection<TenantDescriptor> tenants) {
        return this.apply(message, Collections.unmodifiableCollection(tenants));
    }
}
