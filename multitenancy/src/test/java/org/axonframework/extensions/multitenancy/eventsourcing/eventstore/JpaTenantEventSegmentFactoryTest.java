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
package org.axonframework.extensions.multitenancy.eventsourcing.eventstore;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateBasedJpaEventStorageEngineConfiguration;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link JpaTenantEventSegmentFactory}.
 *
 * @author Theo Emanuelsson
 */
class JpaTenantEventSegmentFactoryTest {

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private Function<TenantDescriptor, EntityManagerFactory> emfProvider;
    private TransactionManager transactionManager;
    private EventConverter eventConverter;
    private JpaTenantEventSegmentFactory testSubject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emfProvider = mock(Function.class);
        transactionManager = mock(TransactionManager.class);
        eventConverter = mock(EventConverter.class);

        // Setup mock to return an EntityManagerFactory for any tenant
        EntityManagerFactory mockEmf = mock(EntityManagerFactory.class);
        when(mockEmf.createEntityManager()).thenReturn(mock(EntityManager.class));
        when(emfProvider.apply(any(TenantDescriptor.class))).thenReturn(mockEmf);

        testSubject = new JpaTenantEventSegmentFactory(emfProvider, transactionManager, eventConverter);
    }

    @Test
    void applyReturnsStorageEngineBackedEventStore() {
        EventStore eventStore = testSubject.apply(TENANT_1);

        assertNotNull(eventStore);
        assertInstanceOf(StorageEngineBackedEventStore.class, eventStore);
    }

    @Test
    void applyReturnsSameEventStoreForSameTenant() {
        EventStore first = testSubject.apply(TENANT_1);
        EventStore second = testSubject.apply(TENANT_1);

        assertSame(first, second);
    }

    @Test
    void applyReturnsDifferentEventStoresForDifferentTenants() {
        EventStore eventStore1 = testSubject.apply(TENANT_1);
        EventStore eventStore2 = testSubject.apply(TENANT_2);

        assertNotSame(eventStore1, eventStore2);
    }

    @Test
    void eventStoreCountReflectsNumberOfCreatedStores() {
        assertEquals(0, testSubject.eventStoreCount());

        testSubject.apply(TENANT_1);
        assertEquals(1, testSubject.eventStoreCount());

        testSubject.apply(TENANT_2);
        assertEquals(2, testSubject.eventStoreCount());

        // Applying same tenant again should not increase count
        testSubject.apply(TENANT_1);
        assertEquals(2, testSubject.eventStoreCount());
    }

    @Test
    void constructorWithCustomConfiguration() {
        TagResolver customTagResolver = new AnnotationBasedTagResolver();

        JpaTenantEventSegmentFactory factory = new JpaTenantEventSegmentFactory(
                emfProvider,
                transactionManager,
                eventConverter,
                c -> c.batchSize(50),
                customTagResolver
        );

        EventStore eventStore = factory.apply(TENANT_1);
        assertNotNull(eventStore);
    }

    @Test
    void constructorRejectsNullEmfProvider() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantEventSegmentFactory(null, transactionManager, eventConverter)
        );
    }

    @Test
    void constructorRejectsNullTransactionManager() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantEventSegmentFactory(emfProvider, null, eventConverter)
        );
    }

    @Test
    void constructorRejectsNullEventConverter() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantEventSegmentFactory(emfProvider, transactionManager, null)
        );
    }

    @Test
    void fullConstructorRejectsNullConfigurer() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantEventSegmentFactory(
                        emfProvider,
                        transactionManager,
                        eventConverter,
                        null,
                        new AnnotationBasedTagResolver()
                )
        );
    }

    @Test
    void fullConstructorRejectsNullTagResolver() {
        assertThrows(NullPointerException.class, () ->
                new JpaTenantEventSegmentFactory(
                        emfProvider,
                        transactionManager,
                        eventConverter,
                        c -> c,
                        null
                )
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
