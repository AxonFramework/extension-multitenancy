package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.common.Registration;

import java.util.List;

/**
 * @author Stefan Dragisic
 */
public interface TenantProvider {

    List<TenantDescriptor> getTenants();

    Registration subscribe(MultiTenantBus bus);

}
