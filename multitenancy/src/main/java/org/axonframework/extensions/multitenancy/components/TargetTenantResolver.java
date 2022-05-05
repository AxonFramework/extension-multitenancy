/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
 * Resolves the target tenant of a given message.
 * <p>
 *
 * @author Stefan Dragisic
 */
public interface TargetTenantResolver<M extends Message<?>> extends BiFunction<M, Collection<TenantDescriptor>, TenantDescriptor> {

    /**
     * Returns {@link TenantDescriptor} for the given message.
     * @param message the message to resolve the target tenant for
     * @param tenants the collection of tenants to resolve the target tenant from (may be empty)
     * @return resolved {@link TenantDescriptor}
     */
    default TenantDescriptor resolveTenant(M message, Collection<TenantDescriptor> tenants) {
        return this.apply(message, Collections.unmodifiableCollection(tenants));
    }
}
