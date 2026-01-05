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
package org.axonframework.extension.multitenancy.messaging.eventhandling.processing;

import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;

import java.util.function.Function;

/**
 * Factory for creating tenant-specific {@link TokenStore} instances.
 * <p>
 * This factory is used by {@link MultiTenantPooledStreamingEventProcessorModule} to create
 * per-tenant token stores for tracking event processing progress.
 * <p>
 * Implementations should ensure that each tenant gets an isolated token store to prevent
 * token conflicts between tenants.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TokenStore
 * @see MultiTenantPooledStreamingEventProcessorModule
 */
@FunctionalInterface
public interface TenantTokenStoreFactory extends Function<TenantDescriptor, TokenStore> {

    /**
     * Creates or retrieves a {@link TokenStore} for the specified tenant.
     *
     * @param tenant The tenant descriptor identifying the tenant.
     * @return A {@link TokenStore} instance for the specified tenant.
     */
    @Override
    TokenStore apply(TenantDescriptor tenant);
}
