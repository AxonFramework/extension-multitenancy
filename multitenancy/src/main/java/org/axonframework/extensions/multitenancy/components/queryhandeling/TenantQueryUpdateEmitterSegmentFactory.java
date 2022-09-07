package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.queryhandling.QueryUpdateEmitter;

import java.util.function.Function;

/**
 * Factory for creating {@link QueryUpdateEmitter} instances that are scoped to a specific {@link TenantDescriptor}.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface TenantQueryUpdateEmitterSegmentFactory extends Function<TenantDescriptor, QueryUpdateEmitter> {

}
