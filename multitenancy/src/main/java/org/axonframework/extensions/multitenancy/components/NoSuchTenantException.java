package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception thrown when a tenant is not found.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class NoSuchTenantException extends AxonNonTransientException {

    public NoSuchTenantException(String tenantId) {
        super("Unknown tenant: " + tenantId);
    }
}
