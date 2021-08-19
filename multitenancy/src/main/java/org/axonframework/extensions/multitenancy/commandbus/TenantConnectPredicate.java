package org.axonframework.extensions.multitenancy.commandbus;

import java.util.function.Predicate;

/**
 * @author Stefan Dragisic
 */
public interface TenantConnectPredicate extends Predicate<TenantDescriptor> {

}
