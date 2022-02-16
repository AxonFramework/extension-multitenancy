package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.Registration;

import java.util.List;

/**
 * Registers new and manages currently registered tenants and {@link MultiTenantAwareComponent} components. If
 * configured monitors tenants changes and updates the {@link MultiTenantAwareComponent} components accordingly.
 * <p>
 *
 * @author Stefan Dragisic
 */
public interface TenantProvider {

    /**
     * @return the list of currently registered tenants.
     */
    List<TenantDescriptor> getTenants();

    /**
     * @param component to be subscribed {@link MultiTenantAwareComponent} for tenant changes.
     * @return the registration for the given component.
     */
    Registration subscribe(MultiTenantAwareComponent component);
}
