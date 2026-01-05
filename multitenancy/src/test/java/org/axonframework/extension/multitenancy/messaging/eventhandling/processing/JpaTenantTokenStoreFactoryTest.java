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

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.conversion.Converter;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.JpaTokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.JpaTokenStoreConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link JpaTenantTokenStoreFactory}.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 */
class JpaTenantTokenStoreFactoryTest {

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private Function<TenantDescriptor, EntityManagerFactory> emfProvider;
    private Converter converter;
    private JpaTenantTokenStoreFactory testSubject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emfProvider = mock(Function.class);
        converter = mock(Converter.class);

        // Setup mock to return an EntityManagerFactory for any tenant
        EntityManagerFactory mockEmf = mock(EntityManagerFactory.class);
        when(mockEmf.createEntityManager()).thenReturn(mock(EntityManager.class));
        when(emfProvider.apply(any(TenantDescriptor.class))).thenReturn(mockEmf);

        testSubject = new JpaTenantTokenStoreFactory(emfProvider, converter);
    }

    @Test
    void applyReturnsJpaTokenStore() {
        TokenStore tokenStore = testSubject.apply(TENANT_1);

        assertNotNull(tokenStore);
        assertInstanceOf(JpaTokenStore.class, tokenStore);
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
        JpaTokenStoreConfiguration customConfig = JpaTokenStoreConfiguration.DEFAULT
                .nodeId("custom-node");

        JpaTenantTokenStoreFactory factory = new JpaTenantTokenStoreFactory(
                emfProvider, converter, customConfig
        );

        TokenStore tokenStore = factory.apply(TENANT_1);
        assertNotNull(tokenStore);
    }

    @Test
    void constructorRejectsNullEmfProvider() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantTokenStoreFactory(null, converter)
        );
    }

    @Test
    void constructorRejectsNullConverter() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantTokenStoreFactory(emfProvider, null)
        );
    }

    @Test
    void constructorRejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantTokenStoreFactory(emfProvider, converter, null)
        );
    }

    @Test
    void emfProviderIsCalledForEachNewTenant() {
        testSubject.apply(TENANT_1);
        testSubject.apply(TENANT_2);
        testSubject.apply(TENANT_1); // Should not call provider again

        verify(emfProvider, times(1)).apply(TENANT_1);
        verify(emfProvider, times(1)).apply(TENANT_2);
    }
}
