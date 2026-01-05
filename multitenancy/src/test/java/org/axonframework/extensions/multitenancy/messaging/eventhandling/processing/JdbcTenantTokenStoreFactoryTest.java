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

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.conversion.Converter;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStoreConfiguration;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link JdbcTenantTokenStoreFactory}.
 *
 * @author Stefan Dragisic
 */
class JdbcTenantTokenStoreFactoryTest {

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private TenantConnectionProviderFactory connectionProviderFactory;
    private Converter converter;
    private JdbcTenantTokenStoreFactory testSubject;

    @BeforeEach
    void setUp() {
        connectionProviderFactory = mock(TenantConnectionProviderFactory.class);
        converter = mock(Converter.class);

        // Setup mock to return a ConnectionProvider for any tenant
        when(connectionProviderFactory.apply(any(TenantDescriptor.class)))
                .thenReturn(mock(ConnectionProvider.class));

        testSubject = new JdbcTenantTokenStoreFactory(connectionProviderFactory, converter);
    }

    @Test
    void applyReturnsJdbcTokenStore() {
        TokenStore tokenStore = testSubject.apply(TENANT_1);

        assertNotNull(tokenStore);
        assertInstanceOf(JdbcTokenStore.class, tokenStore);
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

    @Test
    void constructorWithCustomConfiguration() {
        JdbcTokenStoreConfiguration customConfig = JdbcTokenStoreConfiguration.DEFAULT
                .nodeId("custom-node");

        JdbcTenantTokenStoreFactory factory = new JdbcTenantTokenStoreFactory(
                connectionProviderFactory, converter, customConfig
        );

        TokenStore tokenStore = factory.apply(TENANT_1);
        assertNotNull(tokenStore);
    }

    @Test
    void constructorRejectsNullConnectionProviderFactory() {
        assertThrows(NullPointerException.class, () ->
                new JdbcTenantTokenStoreFactory(null, converter)
        );
    }

    @Test
    void constructorRejectsNullConverter() {
        assertThrows(NullPointerException.class, () ->
                new JdbcTenantTokenStoreFactory(connectionProviderFactory, null)
        );
    }

    @Test
    void constructorRejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () ->
                new JdbcTenantTokenStoreFactory(connectionProviderFactory, converter, null)
        );
    }

    @Test
    void applyUsesConnectionProviderFactory() {
        testSubject.apply(TENANT_1);

        verify(connectionProviderFactory).apply(TENANT_1);
    }

    @Test
    void applyDoesNotCallConnectionProviderFactoryForCachedTenant() {
        testSubject.apply(TENANT_1);
        testSubject.apply(TENANT_1);

        // Should only be called once due to caching
        verify(connectionProviderFactory, times(1)).apply(TENANT_1);
    }
}
