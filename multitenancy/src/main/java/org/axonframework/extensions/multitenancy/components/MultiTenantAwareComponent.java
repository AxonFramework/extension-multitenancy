package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.Registration;

/**
 * Interface for components that can be registered with a {@link TenantProvider}.
 * <p>
 *
 * @author Stefan Dragisic
 */
public interface MultiTenantAwareComponent {

    /**
     * Registers the component with the given {@link TenantProvider} and starts without component. Use when the
     * component should be started by Axon lifecycle.
     * <p>
     *
     * @param tenantDescriptor for the component to register
     * @return registration used to stop the component
     */
    Registration registerTenant(TenantDescriptor tenantDescriptor);

    /**
     * Registers the component with the given {@link TenantProvider} and starts the component immediately. Use when
     * component should be started manually. Typical use case is to start components during runtime when new tenants are
     * created.
     * <p>
     *
     * @param tenantDescriptor for the component to register
     * @return registration used to stop the component
     */
    Registration registerAndStartTenant(TenantDescriptor tenantDescriptor);
}
