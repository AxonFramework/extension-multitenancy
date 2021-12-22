package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.Registration;

/**
 * @author Stefan Dragisic
 */
public interface MultiTenantAwareComponent {

    Registration registerTenant(TenantDescriptor tenantDescriptor);

    Registration registerAndStartTenant(TenantDescriptor tenantDescriptor);

}
