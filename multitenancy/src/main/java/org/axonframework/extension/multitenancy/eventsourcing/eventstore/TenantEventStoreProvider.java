/*
 * Copyright (c) 2010-2025. Axon Framework
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
package org.axonframework.extension.multitenancy.eventsourcing.eventstore;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;

import java.util.Map;

/**
 * Provides access to per-tenant {@link EventStore} segments.
 * <p>
 * This interface abstracts the storage of tenant-specific event stores, allowing components
 * like event processors to access tenant segments without depending on concrete implementations.
 * This design ensures that decorator chains around {@code EventStore} don't affect component lookups.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public interface TenantEventStoreProvider {

    /**
     * Returns the map of tenant descriptors to their corresponding {@link EventStore} segments.
     *
     * @return an unmodifiable view of the tenant segments map
     */
    Map<TenantDescriptor, EventStore> tenantSegments();
}
