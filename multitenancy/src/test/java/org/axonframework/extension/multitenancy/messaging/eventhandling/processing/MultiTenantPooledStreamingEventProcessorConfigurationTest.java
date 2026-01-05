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

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantPooledStreamingEventProcessorConfiguration}.
 *
 * @author Theo Emanuelsson
 */
class MultiTenantPooledStreamingEventProcessorConfigurationTest {

    @Test
    void tenantTokenStoreFactoryIsNullByDefault() {
        var config = new MultiTenantPooledStreamingEventProcessorConfiguration();

        assertNull(config.tenantTokenStoreFactory());
    }

    @Test
    void tenantTokenStoreFactoryCanBeSet() {
        TenantTokenStoreFactory factory = mock(TenantTokenStoreFactory.class);
        var config = new MultiTenantPooledStreamingEventProcessorConfiguration();

        config.tenantTokenStoreFactory(factory);

        assertEquals(factory, config.tenantTokenStoreFactory());
    }

    @Test
    void tenantTokenStoreFactoryRejectsNull() {
        var config = new MultiTenantPooledStreamingEventProcessorConfiguration();

        assertThrows(NullPointerException.class, () -> config.tenantTokenStoreFactory(null));
    }

    @Test
    void fluentMethodsReturnCorrectType() {
        TenantTokenStoreFactory factory = mock(TenantTokenStoreFactory.class);
        var config = new MultiTenantPooledStreamingEventProcessorConfiguration();

        // Verify fluent methods return the correct subtype for chaining
        var result = config
                .batchSize(100)
                .tenantTokenStoreFactory(factory)
                .initialSegmentCount(8);

        assertInstanceOf(MultiTenantPooledStreamingEventProcessorConfiguration.class, result);
        assertEquals(factory, result.tenantTokenStoreFactory());
        assertEquals(100, result.batchSize());
        assertEquals(8, result.initialSegmentCount());
    }

    @Test
    void tenantTokenStoreFactoryReturnsThisForChaining() {
        TenantTokenStoreFactory factory = mock(TenantTokenStoreFactory.class);
        var config = new MultiTenantPooledStreamingEventProcessorConfiguration();

        var result = config.tenantTokenStoreFactory(factory);

        assertSame(config, result);
    }
}
