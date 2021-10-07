package org.axonframework.extensions.multitenancy;

import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.commandbus.TenantDescriptor;

/**
 * @author Stefan Dragisic
 */
public interface MultiTenantAwareComponent {

    Registration registerTenant(TenantDescriptor tenantDescriptor);

    Registration registerTenantAndSubscribe(TenantDescriptor tenantDescriptor);

}
