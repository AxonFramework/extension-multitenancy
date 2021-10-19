package org.axonframework.extensions.multitenancy.components;

import java.util.function.Predicate;

/**
 * @author Stefan Dragisic
 */
public interface TenantConnectPredicate extends Predicate<TenantDescriptor> {

}
