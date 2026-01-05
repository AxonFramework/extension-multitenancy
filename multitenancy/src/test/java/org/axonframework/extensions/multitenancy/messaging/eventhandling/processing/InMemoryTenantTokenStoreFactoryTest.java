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
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link InMemoryTenantTokenStoreFactory}.
 *
 * @author Stefan Dragisic
 */
class InMemoryTenantTokenStoreFactoryTest {

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private InMemoryTenantTokenStoreFactory testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new InMemoryTenantTokenStoreFactory();
    }

    @Test
    void applyReturnsTokenStoreForTenant() {
        TokenStore tokenStore = testSubject.apply(TENANT_1);

        assertNotNull(tokenStore);
    }

    @Test
    void applyReturnsSameTokenStoreForSameTenant() {
        TokenStore first = testSubject.apply(TENANT_1);
        TokenStore second = testSubject.apply(TENANT_1);

        assertSame(first, second);
    }

    @Test
    void applyReturnsDifferentTokenStoresForDifferentTenants() {
        TokenStore tokenStore1 = testSubject.apply(TENANT_1);
        TokenStore tokenStore2 = testSubject.apply(TENANT_2);

        assertNotSame(tokenStore1, tokenStore2);
    }

    @Test
    void tokenStoreCountReflectsNumberOfCreatedStores() {
        assertEquals(0, testSubject.tokenStoreCount());

        testSubject.apply(TENANT_1);
        assertEquals(1, testSubject.tokenStoreCount());

        testSubject.apply(TENANT_2);
        assertEquals(2, testSubject.tokenStoreCount());

        // Applying same tenant again should not increase count
        testSubject.apply(TENANT_1);
        assertEquals(2, testSubject.tokenStoreCount());
    }
}
