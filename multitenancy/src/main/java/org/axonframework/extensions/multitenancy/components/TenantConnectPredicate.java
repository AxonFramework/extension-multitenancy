package org.axonframework.extensions.multitenancy.components;

import java.util.function.Predicate;

/**
 * Predicate that in runtime determines whether a tenant should be connected to. Used for dynamic registration of
 * tenant-specific components.
 * <p>
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface TenantConnectPredicate extends Predicate<TenantDescriptor> {

}
