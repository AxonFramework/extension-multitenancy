package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.common.AxonNonTransientException;

public class NoSuchTenantException extends AxonNonTransientException {
    public NoSuchTenantException(String tenantId) {
        super("Unknown tenant: " + tenantId);
    }
}
