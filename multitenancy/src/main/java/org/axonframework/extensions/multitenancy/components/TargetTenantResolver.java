/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.multitenancy.components;

import org.axonframework.messaging.Message;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

/**
 * Resolves the target tenant of a given {@link Message} implementation of type {@code M}.
 *
 * @param <M> The {@link Message} implementation this resolver acts on.
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface TargetTenantResolver<M extends Message<?>>
        extends BiFunction<M, Collection<TenantDescriptor>, TenantDescriptor> {

    /**
     * Returns {@link TenantDescriptor} for the given {@code message}.
     *
     * @param message The {@link Message} implementation to resolve the target tenant for.
     * @param tenants The collection of tenants to resolve the target tenant from. May be empty.
     * @return The resolved {@link TenantDescriptor} based on the given {@code message}.
     */
    default TenantDescriptor resolveTenant(M message, Collection<TenantDescriptor> tenants) {
        return this.apply(message, Collections.unmodifiableCollection(tenants));
    }
}
