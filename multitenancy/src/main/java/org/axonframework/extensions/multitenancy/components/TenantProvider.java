package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.Registration;

import java.util.List;

/**
 * TenantProvider monitors for tenant changes, manages tenants and {@link MultiTenantAwareComponent}
 *
 * @author Stefan Dragisic
 */
public interface TenantProvider {

    List<TenantDescriptor> getTenants();

    Registration subscribe(MultiTenantAwareComponent bus);

}
