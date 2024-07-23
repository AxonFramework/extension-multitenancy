/*
 * Copyright (c) 2010-2024. Axon Framework
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

import java.util.Map;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;

/**
 * Interface for multi-tenant message sources that can provide tenant segments.
 *
 * @author Stefan Dragisic
 * @param <T> The type of the tenant segment, which must extend MessageSource
 * @since 4.10.0
 */
public interface MultiTenantSubscribableMessageSource<T extends SubscribableMessageSource<EventMessage<?>>> {

    /**
     * Returns a map of tenant segments, where the key is the TenantDescriptor
     * and the value is the corresponding tenant segment of type T, which extends {@link SubscribableMessageSource}.
     *
     * @return A map of TenantDescriptor to tenant segments
     */
    Map<TenantDescriptor, T> tenantSegments();
}
