package org.axonframework.extensions.multitenancy.components.queryhandeling;

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.queryhandling.QueryUpdateEmitter;

import java.util.function.Function;

/**
 * @author Stefan Dragisic
 */
public interface TenantQueryUpdateEmitterSegmentFactory extends Function<TenantDescriptor, QueryUpdateEmitter> {

}
