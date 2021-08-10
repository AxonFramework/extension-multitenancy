package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.messaging.Message;

import java.util.function.Function;

/**
 * @author Stefan Dragisic
 */
public interface TargetTenantResolver<M extends Message<?>> extends Function<M, String> {

    /**
     * @param message
     * @return
     */
    default String resolveTenant(M message) {
        return this.apply(message);
    }
}
