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
package org.axonframework.extensions.multitenancy.axonserver;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.junit.jupiter.api.*;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AxonServerTenantEventSegmentFactory}.
 *
 * @author Stefan Dragisic
 */
class AxonServerTenantEventSegmentFactoryTest {

    private Configuration configuration;
    private TagResolver tagResolver;

    @BeforeEach
    void setUp() {
        configuration = mock(Configuration.class);
        tagResolver = new AnnotationBasedTagResolver();
    }

    @Test
    void createFromReturnsFactoryWhenConnectionManagerAvailable() {
        when(configuration.hasComponent(AxonServerConnectionManager.class)).thenReturn(true);
        when(configuration.getComponent(eq(TagResolver.class), (Supplier<TagResolver>) any())).thenReturn(tagResolver);

        AxonServerTenantEventSegmentFactory result = AxonServerTenantEventSegmentFactory.createFrom(configuration);

        assertNotNull(result);
    }

    @Test
    void createFromReturnsNullWhenConnectionManagerNotAvailable() {
        when(configuration.hasComponent(AxonServerConnectionManager.class)).thenReturn(false);

        AxonServerTenantEventSegmentFactory result = AxonServerTenantEventSegmentFactory.createFrom(configuration);

        assertNull(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void createFromUsesDefaultTagResolverWhenNotConfigured() {
        when(configuration.hasComponent(AxonServerConnectionManager.class)).thenReturn(true);
        when(configuration.getComponent(eq(TagResolver.class), (Supplier<TagResolver>) any()))
                .thenAnswer(invocation -> {
                    // Return the default from the supplier
                    return invocation.getArgument(1, Supplier.class).get();
                });

        AxonServerTenantEventSegmentFactory result = AxonServerTenantEventSegmentFactory.createFrom(configuration);

        assertNotNull(result);
    }

    @Test
    void constructorRejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () ->
                new AxonServerTenantEventSegmentFactory(null, tagResolver)
        );
    }

    @Test
    void constructorRejectsNullTagResolver() {
        assertThrows(NullPointerException.class, () ->
                new AxonServerTenantEventSegmentFactory(configuration, null)
        );
    }
}
