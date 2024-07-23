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
package org.axonframework.extensions.multitenancy.autoconfig;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.config.Configuration;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.common.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiTenantPersistentStreamMessageSourceTest {

    @Mock
    private Configuration configuration;
    @Mock
    private TenantPersistentStreamMessageSourceFactory factory;
    @Mock
    private PersistentStreamProperties persistentStreamProperties;
    @Mock
    private ScheduledExecutorService scheduler;
    @Mock
    private PersistentStreamMessageSource mockTenantSource;

    private MultiTenantPersistentStreamMessageSource source;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        source = new MultiTenantPersistentStreamMessageSource(
                "testName",
                persistentStreamProperties,
                scheduler,
                100,
                "testContext",
                configuration,
                factory
        );
    }

    @Test
    void registerTenant() {
        TenantDescriptor descriptor = new TenantDescriptor("testTenant");
        when(factory.build(anyString(), any(), any(), anyInt(), anyString(), any(), eq(descriptor)))
                .thenReturn(mockTenantSource);

        Registration registration = source.registerTenant(descriptor);

        assertNotNull(registration);
        verify(factory).build("testName", persistentStreamProperties, scheduler, 100, "testContext", configuration, descriptor);

        Map<TenantDescriptor, PersistentStreamMessageSource> segments = source.tenantSegments();
        assertEquals(1, segments.size());
        assertTrue(segments.containsKey(descriptor));
        assertEquals(mockTenantSource, segments.get(descriptor));
    }

    @Test
    void registerAndStartTenant() {
        TenantDescriptor descriptor = new TenantDescriptor("testTenant");
        when(factory.build(anyString(), any(), any(), anyInt(), anyString(), any(), eq(descriptor)))
                .thenReturn(mockTenantSource);

        Registration registration = source.registerAndStartTenant(descriptor);

        assertNotNull(registration);
        verify(factory).build("testName", persistentStreamProperties, scheduler, 100, "testContext", configuration, descriptor);

        Map<TenantDescriptor, PersistentStreamMessageSource> segments = source.tenantSegments();
        assertEquals(1, segments.size());
        assertTrue(segments.containsKey(descriptor));
        assertEquals(mockTenantSource, segments.get(descriptor));
    }

    @Test
    void unregisterTenant() {
        TenantDescriptor descriptor = new TenantDescriptor("testTenant");
        when(factory.build(anyString(), any(), any(), anyInt(), anyString(), any(), eq(descriptor)))
                .thenReturn(mockTenantSource);

        Registration registration = source.registerTenant(descriptor);
        assertTrue(registration.cancel());

        Map<TenantDescriptor, PersistentStreamMessageSource> segments = source.tenantSegments();
        assertTrue(segments.isEmpty());
    }

    @Test
    void registerMultipleTenants() {
        TenantDescriptor descriptor1 = new TenantDescriptor("tenant1");
        TenantDescriptor descriptor2 = new TenantDescriptor("tenant2");

        when(factory.build(anyString(), any(), any(), anyInt(), anyString(), any(), eq(descriptor1)))
                .thenReturn(mockTenantSource);
        when(factory.build(anyString(), any(), any(), anyInt(), anyString(), any(), eq(descriptor2)))
                .thenReturn(mockTenantSource);

        source.registerTenant(descriptor1);
        source.registerTenant(descriptor2);

        Map<TenantDescriptor, PersistentStreamMessageSource> segments = source.tenantSegments();
        assertEquals(2, segments.size());
        assertTrue(segments.containsKey(descriptor1));
        assertTrue(segments.containsKey(descriptor2));
    }

    @Test
    void tenantPersistentStreamMessageSourceFactory() {
        TenantPersistentStreamMessageSourceFactory testFactory =
                (name, props, sched, batch, ctx, config, tenant) -> {
                    assertEquals("testName", name);
                    assertEquals(persistentStreamProperties, props);
                    assertEquals(scheduler, sched);
                    assertEquals(100, batch);
                    assertEquals("testContext", ctx);
                    assertEquals(configuration, config);
                    assertEquals(new TenantDescriptor("testTenant"), tenant);
                    return mockTenantSource;
                };

        PersistentStreamMessageSource result = testFactory.build(
                "testName", persistentStreamProperties, scheduler, 100, "testContext",
                configuration, new TenantDescriptor("testTenant")
        );

        assertEquals(mockTenantSource, result);
    }
}