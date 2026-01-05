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

package org.axonframework.extensions.multitenancy.core.configuration;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.extensions.multitenancy.core.MetadataBasedTenantResolver;
import org.axonframework.extensions.multitenancy.core.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.core.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.core.TenantProvider;
import org.axonframework.extensions.multitenancy.eventsourcing.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.commandhandling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.eventhandling.processing.TenantEventProcessorSegmentFactory;
import org.axonframework.extensions.multitenancy.messaging.queryhandling.TenantQuerySegmentFactory;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenancyConfigurer}.
 *
 * @author Stefan Dragisic
 * @since 5.0.0
 */
class MultiTenancyConfigurerTest {

    private MultiTenancyConfigurer testSubject;

    @BeforeEach
    void setUp() {
        testSubject = MultiTenancyConfigurer.enhance(MessagingConfigurer.create());
    }

    @Test
    void registerTenantProviderMakesProviderRetrievable() {
        TenantProvider expected = mock(TenantProvider.class);

        Configuration result = testSubject.registerTenantProvider(config -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TenantProvider.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void registerTargetTenantResolverMakesResolverRetrievable() {
        TargetTenantResolver<Message> expected = new MetadataBasedTenantResolver();

        Configuration result = testSubject.registerTargetTenantResolver(config -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TargetTenantResolver.class));
    }

    @Test
    void registerTenantConnectPredicateMakesPredicateRetrievable() {
        TenantConnectPredicate expected = tenant -> true;

        Configuration result = testSubject.registerTenantConnectPredicate(config -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TenantConnectPredicate.class));
    }

    @Test
    void registerCommandBusSegmentFactoryMakesFactoryRetrievable() {
        TenantCommandSegmentFactory expected = mock(TenantCommandSegmentFactory.class);

        Configuration result = testSubject.registerCommandBusSegmentFactory(config -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TenantCommandSegmentFactory.class));
    }

    @Test
    void registerQueryBusSegmentFactoryMakesFactoryRetrievable() {
        TenantQuerySegmentFactory expected = mock(TenantQuerySegmentFactory.class);

        Configuration result = testSubject.registerQueryBusSegmentFactory(config -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TenantQuerySegmentFactory.class));
    }

    @Test
    void registerEventStoreSegmentFactoryMakesFactoryRetrievable() {
        TenantEventSegmentFactory expected = mock(TenantEventSegmentFactory.class);

        Configuration result = testSubject.registerEventStoreSegmentFactory(config -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TenantEventSegmentFactory.class));
    }

    @Test
    void registerEventProcessorSegmentFactoryMakesFactoryRetrievable() {
        TenantEventProcessorSegmentFactory expected = mock(TenantEventProcessorSegmentFactory.class);

        Configuration result = testSubject.registerEventProcessorSegmentFactory(config -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TenantEventProcessorSegmentFactory.class));
    }

    @Test
    void enhanceWithNullConfigurerThrowsException() {
        assertThrows(NullPointerException.class, () -> MultiTenancyConfigurer.enhance((MessagingConfigurer) null));
    }

    @Test
    void fluentApiReturnsSameInstance() {
        TenantProvider tenantProvider = mock(TenantProvider.class);
        TenantCommandSegmentFactory commandFactory = mock(TenantCommandSegmentFactory.class);

        MultiTenancyConfigurer result = testSubject
                .registerTenantProvider(config -> tenantProvider)
                .registerCommandBusSegmentFactory(config -> commandFactory);

        assertSame(testSubject, result);
    }

    @Test
    void multipleRegistrationsCanBeChained() {
        TenantProvider tenantProvider = mock(TenantProvider.class);
        TargetTenantResolver<Message> resolver = new MetadataBasedTenantResolver();
        TenantConnectPredicate predicate = tenant -> true;
        TenantCommandSegmentFactory commandFactory = mock(TenantCommandSegmentFactory.class);
        TenantQuerySegmentFactory queryFactory = mock(TenantQuerySegmentFactory.class);
        TenantEventSegmentFactory eventFactory = mock(TenantEventSegmentFactory.class);
        TenantEventProcessorSegmentFactory processorFactory = mock(TenantEventProcessorSegmentFactory.class);

        Configuration result = testSubject
                .registerTenantProvider(config -> tenantProvider)
                .registerTargetTenantResolver(config -> resolver)
                .registerTenantConnectPredicate(config -> predicate)
                .registerCommandBusSegmentFactory(config -> commandFactory)
                .registerQueryBusSegmentFactory(config -> queryFactory)
                .registerEventStoreSegmentFactory(config -> eventFactory)
                .registerEventProcessorSegmentFactory(config -> processorFactory)
                .build();

        assertEquals(tenantProvider, result.getComponent(TenantProvider.class));
        assertEquals(resolver, result.getComponent(TargetTenantResolver.class));
        assertEquals(predicate, result.getComponent(TenantConnectPredicate.class));
        assertEquals(commandFactory, result.getComponent(TenantCommandSegmentFactory.class));
        assertEquals(queryFactory, result.getComponent(TenantQuerySegmentFactory.class));
        assertEquals(eventFactory, result.getComponent(TenantEventSegmentFactory.class));
        assertEquals(processorFactory, result.getComponent(TenantEventProcessorSegmentFactory.class));
    }
}
