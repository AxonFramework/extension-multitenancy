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
package org.axonframework.extensions.multitenancy.messaging.eventhandling.processing;

import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link TenantTokenStoreFactory} that creates per-tenant
 * {@link InMemoryTokenStore} instances.
 * <p>
 * This factory caches created token stores by tenant, ensuring the same store is returned
 * for subsequent calls with the same tenant. Token stores created by this factory store
 * tokens in memory and will lose all tokens when the application restarts.
 * <p>
 * For production use with persistent token storage, consider using a different implementation
 * such as the Axon Server token store factory or a JPA-based implementation.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TenantTokenStoreFactory
 * @see InMemoryTokenStore
 */
public class InMemoryTenantTokenStoreFactory implements TenantTokenStoreFactory {

    private final Map<TenantDescriptor, TokenStore> tokenStores = new ConcurrentHashMap<>();

    @Override
    public TokenStore apply(TenantDescriptor tenant) {
        return tokenStores.computeIfAbsent(tenant, t -> new InMemoryTokenStore());
    }

    /**
     * Returns the number of token stores currently cached.
     * Primarily for testing purposes.
     *
     * @return The number of cached token stores.
     */
    int tokenStoreCount() {
        return tokenStores.size();
    }
}
