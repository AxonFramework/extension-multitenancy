package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.common.Registration;

/**
 * @author Stefan Dragisic
 */
public interface MultiTenantBus {

    Registration registerTenant(TenantDescriptor tenantDescriptor);

    void registerAndSubscribeTenant(TenantDescriptor tenantDescriptor);

}
