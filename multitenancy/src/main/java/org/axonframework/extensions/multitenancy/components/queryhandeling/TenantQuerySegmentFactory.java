package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.queryhandling.QueryBus;

import java.util.function.Function;

/**
 * Factory for creating {@link QueryBus} instances for a given {@link TenantDescriptor}.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface TenantQuerySegmentFactory extends Function<TenantDescriptor, QueryBus> {

}
