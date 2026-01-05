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
package org.axonframework.extensions.multitenancy.core;

import org.axonframework.common.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SimpleTenantProviderTest {

    private SimpleTenantProvider provider;
    private TenantDescriptor tenant1;
    private TenantDescriptor tenant2;

    @BeforeEach
    void setUp() {
        provider = new SimpleTenantProvider();
        tenant1 = TenantDescriptor.tenantWithId("tenant-1");
        tenant2 = TenantDescriptor.tenantWithId("tenant-2");
    }

    @Test
    void addTenantReturnsTrueWhenTenantIsNew() {
        assertTrue(provider.addTenant(tenant1));
        assertEquals(1, provider.getTenants().size());
        assertTrue(provider.hasTenant(tenant1));
    }

    @Test
    void addTenantReturnsFalseWhenTenantAlreadyExists() {
        provider.addTenant(tenant1);
        assertFalse(provider.addTenant(tenant1));
        assertEquals(1, provider.getTenants().size());
    }

    @Test
    void addTenantNotifiesSubscribers() {
        MultiTenantAwareComponent subscriber = mock(MultiTenantAwareComponent.class);
        provider.subscribe(subscriber);

        provider.addTenant(tenant1);

        verify(subscriber).registerAndStartTenant(tenant1);
    }

    @Test
    void removeTenantReturnsTrueWhenTenantExists() {
        provider.addTenant(tenant1);
        assertTrue(provider.removeTenant(tenant1));
        assertFalse(provider.hasTenant(tenant1));
    }

    @Test
    void removeTenantReturnsFalseWhenTenantDoesNotExist() {
        assertFalse(provider.removeTenant(tenant1));
    }

    @Test
    void removeTenantByIdWorks() {
        provider.addTenant(tenant1);
        assertTrue(provider.removeTenant("tenant-1"));
        assertFalse(provider.hasTenant("tenant-1"));
    }

    @Test
    void subscribeRegistersExistingTenants() {
        provider.addTenant(tenant1);
        provider.addTenant(tenant2);

        MultiTenantAwareComponent subscriber = mock(MultiTenantAwareComponent.class);
        provider.subscribe(subscriber);

        verify(subscriber).registerTenant(tenant1);
        verify(subscriber).registerTenant(tenant2);
    }

    @Test
    void unsubscribeStopsNotifications() {
        MultiTenantAwareComponent subscriber = mock(MultiTenantAwareComponent.class);
        Registration registration = provider.subscribe(subscriber);

        registration.cancel();
        provider.addTenant(tenant1);

        verify(subscriber, never()).registerAndStartTenant(tenant1);
    }

    @Test
    void constructorWithInitialTenants() {
        SimpleTenantProvider providerWithTenants = new SimpleTenantProvider(List.of(tenant1, tenant2));

        assertEquals(2, providerWithTenants.getTenants().size());
        assertTrue(providerWithTenants.hasTenant(tenant1));
        assertTrue(providerWithTenants.hasTenant(tenant2));
    }

    @Test
    void addTenantsAddsMutlipleTenants() {
        provider.addTenants(List.of(tenant1, tenant2));

        assertEquals(2, provider.getTenants().size());
        assertTrue(provider.hasTenant(tenant1));
        assertTrue(provider.hasTenant(tenant2));
    }

    @Test
    void isThreadSafe() throws InterruptedException {
        AtomicInteger addCount = new AtomicInteger(0);
        MultiTenantAwareComponent subscriber = mock(MultiTenantAwareComponent.class);
        provider.subscribe(subscriber);

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            int tenantNum = i;
            threads[i] = new Thread(() -> {
                TenantDescriptor tenant = TenantDescriptor.tenantWithId("tenant-" + tenantNum);
                if (provider.addTenant(tenant)) {
                    addCount.incrementAndGet();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(10, addCount.get());
        assertEquals(10, provider.getTenants().size());
    }

    @Test
    void removeTenantTriggersCleanupRegistrations() {
        AtomicInteger cleanupCount = new AtomicInteger(0);

        MultiTenantAwareComponent subscriber = new MultiTenantAwareComponent() {
            @Override
            public Registration registerTenant(TenantDescriptor tenantDescriptor) {
                return () -> {
                    cleanupCount.incrementAndGet();
                    return true;
                };
            }

            @Override
            public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
                return registerTenant(tenantDescriptor);
            }
        };

        provider.subscribe(subscriber);
        provider.addTenant(tenant1);

        assertEquals(0, cleanupCount.get());

        provider.removeTenant(tenant1);

        assertEquals(1, cleanupCount.get());
    }

    @Test
    void removeTenantTriggersCleanupForAllSubscribers() {
        AtomicInteger cleanupCount = new AtomicInteger(0);

        MultiTenantAwareComponent subscriber1 = createCleanupCountingSubscriber(cleanupCount);
        MultiTenantAwareComponent subscriber2 = createCleanupCountingSubscriber(cleanupCount);

        provider.subscribe(subscriber1);
        provider.subscribe(subscriber2);
        provider.addTenant(tenant1);

        assertEquals(0, cleanupCount.get());

        provider.removeTenant(tenant1);

        assertEquals(2, cleanupCount.get());
    }

    @Test
    void subscribeAfterAddTenantStillGetsCleanupOnRemove() {
        AtomicInteger cleanupCount = new AtomicInteger(0);

        // Add tenant first
        provider.addTenant(tenant1);

        // Subscribe after
        MultiTenantAwareComponent subscriber = createCleanupCountingSubscriber(cleanupCount);
        provider.subscribe(subscriber);

        // Cleanup should still be triggered
        provider.removeTenant(tenant1);

        assertEquals(1, cleanupCount.get());
    }

    private MultiTenantAwareComponent createCleanupCountingSubscriber(AtomicInteger cleanupCount) {
        return new MultiTenantAwareComponent() {
            @Override
            public Registration registerTenant(TenantDescriptor tenantDescriptor) {
                return () -> {
                    cleanupCount.incrementAndGet();
                    return true;
                };
            }

            @Override
            public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
                return registerTenant(tenantDescriptor);
            }
        };
    }
}
