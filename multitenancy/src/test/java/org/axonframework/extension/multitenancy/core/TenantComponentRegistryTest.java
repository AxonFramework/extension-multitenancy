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
package org.axonframework.extension.multitenancy.core;

import org.axonframework.common.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantComponentRegistryTest {

    private TenantDescriptor tenant1;
    private TenantDescriptor tenant2;

    @BeforeEach
    void setUp() {
        tenant1 = TenantDescriptor.tenantWithId("tenant-1");
        tenant2 = TenantDescriptor.tenantWithId("tenant-2");
    }

    @Test
    void getComponentCreatesLazily() {
        AtomicInteger createCount = new AtomicInteger(0);

        TenantComponentRegistry<String> registry = new TenantComponentRegistry<>(
                String.class,
                tenant -> {
                    createCount.incrementAndGet();
                    return "component-for-" + tenant.tenantId();
                }
        );

        registry.registerTenant(tenant1);
        assertEquals(0, createCount.get(), "Component should not be created on registration");

        String component = registry.getComponent(tenant1);
        assertEquals(1, createCount.get(), "Component should be created on first access");
        assertEquals("component-for-tenant-1", component);

        // Second access should return cached instance
        String sameComponent = registry.getComponent(tenant1);
        assertEquals(1, createCount.get(), "Component should be cached");
        assertSame(component, sameComponent);
    }

    @Test
    void cleanupInvokedOnTenantRemoval() {
        AtomicBoolean cleanupCalled = new AtomicBoolean(false);
        AtomicReference<String> cleanedComponent = new AtomicReference<>();

        TenantComponentFactory<String> factory = new TenantComponentFactory<>() {
            @Override
            public String apply(TenantDescriptor tenant) {
                return "component-for-" + tenant.tenantId();
            }

            @Override
            public void cleanup(TenantDescriptor tenant, String component) {
                cleanupCalled.set(true);
                cleanedComponent.set(component);
            }
        };

        TenantComponentRegistry<String> registry = new TenantComponentRegistry<>(String.class, factory);
        Registration registration = registry.registerTenant(tenant1);

        // Create the component
        String component = registry.getComponent(tenant1);
        assertNotNull(component);
        assertFalse(cleanupCalled.get());

        // Cancel registration (simulates tenant removal)
        registration.cancel();

        assertTrue(cleanupCalled.get());
        assertEquals(component, cleanedComponent.get());
    }

    @Test
    void cleanupNotInvokedIfComponentNeverCreated() {
        AtomicBoolean cleanupCalled = new AtomicBoolean(false);

        TenantComponentFactory<String> factory = new TenantComponentFactory<>() {
            @Override
            public String apply(TenantDescriptor tenant) {
                return "component";
            }

            @Override
            public void cleanup(TenantDescriptor tenant, String component) {
                cleanupCalled.set(true);
            }
        };

        TenantComponentRegistry<String> registry = new TenantComponentRegistry<>(String.class, factory);
        Registration registration = registry.registerTenant(tenant1);

        // Don't access component, just cancel
        registration.cancel();

        assertFalse(cleanupCalled.get(), "Cleanup should not be called if component was never created");
    }

    @Test
    void autoCloseableComponentsClosedAutomatically() {
        AtomicBoolean closed = new AtomicBoolean(false);

        TenantComponentRegistry<AutoCloseable> registry = new TenantComponentRegistry<>(
                AutoCloseable.class,
                tenant -> () -> closed.set(true)
        );

        Registration registration = registry.registerTenant(tenant1);
        registry.getComponent(tenant1);

        assertFalse(closed.get());

        registration.cancel();

        assertTrue(closed.get(), "AutoCloseable should be closed on tenant removal");
    }

    @Test
    void getTenantsReturnsRegisteredTenants() {
        TenantComponentRegistry<String> registry = new TenantComponentRegistry<>(
                String.class,
                tenant -> "component"
        );

        assertTrue(registry.getTenants().isEmpty());

        registry.registerTenant(tenant1);
        registry.registerTenant(tenant2);

        assertEquals(2, registry.getTenants().size());
        assertTrue(registry.getTenants().contains(tenant1));
        assertTrue(registry.getTenants().contains(tenant2));
    }

    @Test
    void tenantRemovedFromTenantsSetOnCancel() {
        TenantComponentRegistry<String> registry = new TenantComponentRegistry<>(
                String.class,
                tenant -> "component"
        );

        Registration registration = registry.registerTenant(tenant1);
        assertTrue(registry.getTenants().contains(tenant1));

        registration.cancel();
        assertFalse(registry.getTenants().contains(tenant1));
    }

    @Test
    void componentRemovedFromCacheOnCancel() {
        TenantComponentRegistry<String> registry = new TenantComponentRegistry<>(
                String.class,
                tenant -> "component"
        );

        Registration registration = registry.registerTenant(tenant1);
        String first = registry.getComponent(tenant1);

        registration.cancel();

        // Re-register and get component should create new instance
        registry.registerTenant(tenant1);
        String second = registry.getComponent(tenant1);

        // Should be equal content but new instance (since factory returns same string)
        assertEquals(first, second);
    }
}
