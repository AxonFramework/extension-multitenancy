package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception thrown when a tenant is not found.
 *
 * @author Stefan Dragisic
 */
public class NoSuchTenantException extends AxonNonTransientException {

    public NoSuchTenantException(String tenantId) {
        super("Unknown tenant: " + tenantId);
    }
}
