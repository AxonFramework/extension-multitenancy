package org.axonframework.extensions.multitenancy.components;

import java.util.function.Function;

/**
 * Maps a tenant id to the context name for the EventProcessorControlService.
 * <p>
 * This interface is used to create a mapping between a given {@link TenantDescriptor} and a context name.
 * After a mapping is created, it will be used by EventProcessorControlService
 * to associate event processor control with the given context.
 *
 * @author Stefan Dragisic
 * @since 4.9.3
 */
public interface TenantEventProcessorControlSegmentFactory extends Function<TenantDescriptor, String> {

}