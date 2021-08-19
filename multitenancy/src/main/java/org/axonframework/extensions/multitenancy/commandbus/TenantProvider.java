package org.axonframework.extensions.multitenancy.commandbus;

import java.util.List;
import java.util.function.Supplier;

/**
 * @author Stefan Dragisic
 */
public interface TenantProvider extends Supplier<List<TenantDescriptor>> {

    default void subscribeTenantUpdates(MultiTenantBus bus) {
    }

}
