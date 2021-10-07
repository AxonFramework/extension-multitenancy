package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.MultiTenantAwareComponent;

import java.util.List;

/**
 * @author Stefan Dragisic
 */
public interface TenantProvider {

    List<TenantDescriptor> getTenants();

    Registration subscribe(MultiTenantAwareComponent bus);

}
