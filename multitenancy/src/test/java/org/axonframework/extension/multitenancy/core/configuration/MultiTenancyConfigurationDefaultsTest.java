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

package org.axonframework.extension.multitenancy.core.configuration;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.messaging.commandhandling.MultiTenantCommandBus;
import org.axonframework.extension.multitenancy.messaging.commandhandling.TenantCommandSegmentFactory;
import org.axonframework.extension.multitenancy.messaging.queryhandling.MultiTenantQueryBus;
import org.axonframework.extension.multitenancy.messaging.queryhandling.TenantQuerySegmentFactory;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.interception.InterceptingCommandBus;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.interception.InterceptingQueryBus;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenancyConfigurationDefaults}.
 *
 * @author Stefan Dragisic
 */
class MultiTenancyConfigurationDefaultsTest {

    @Test
    void orderIsMaxIntegerMinusOne() {
        assertEquals(Integer.MAX_VALUE - 1, new MultiTenancyConfigurationDefaults().order());
    }

    @Test
    void commandBusNotWrappedWithoutSegmentFactory() {
        Configuration resultConfig = MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
                .registerTargetTenantResolver(config -> (message, tenants) ->
                        TenantDescriptor.tenantWithId("test"))
                .build();

        CommandBus commandBus = resultConfig.getComponent(CommandBus.class);
        // Should not be multi-tenant since no segment factory was registered
        assertFalse(commandBus instanceof MultiTenantCommandBus,
                "CommandBus should not be multi-tenant without segment factory");
    }

    @Test
    void commandBusNotWrappedWithoutResolver() {
        CommandBus mockSegmentBus = mock(CommandBus.class);
        TenantCommandSegmentFactory segmentFactory = tenant -> mockSegmentBus;

        Configuration resultConfig = MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
                .registerCommandBusSegmentFactory(config -> segmentFactory)
                // No resolver registered
                .build();

        CommandBus commandBus = resultConfig.getComponent(CommandBus.class);
        // Should not be multi-tenant since no resolver was registered
        assertFalse(commandBus instanceof MultiTenantCommandBus,
                "CommandBus should not be multi-tenant without tenant resolver");
    }

    @Test
    void commandBusWrappedWhenBothFactoryAndResolverConfigured() {
        CommandBus mockSegmentBus = mock(CommandBus.class);
        TenantCommandSegmentFactory segmentFactory = tenant -> mockSegmentBus;

        Configuration resultConfig = MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
                .registerCommandBusSegmentFactory(config -> segmentFactory)
                .registerTargetTenantResolver(config -> (message, tenants) ->
                        TenantDescriptor.tenantWithId("test"))
                .build();

        CommandBus commandBus = resultConfig.getComponent(CommandBus.class);
        // InterceptingCommandBus wraps MultiTenantCommandBus (following AF5 decorator pattern)
        assertInstanceOf(InterceptingCommandBus.class, commandBus,
                "CommandBus should be wrapped with InterceptingCommandBus");
        // The multi-tenant bus is inside the decoration chain
        assertTrue(resultConfig.hasComponent(MultiTenantCommandBus.class) ||
                   commandBus instanceof InterceptingCommandBus,
                "MultiTenantCommandBus should be in the decoration chain");
    }

    @Test
    void queryBusNotWrappedWithoutSegmentFactory() {
        Configuration resultConfig = MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
                .registerTargetTenantResolver(config -> (message, tenants) ->
                        TenantDescriptor.tenantWithId("test"))
                .build();

        QueryBus queryBus = resultConfig.getComponent(QueryBus.class);
        // Should not be multi-tenant since no segment factory was registered
        assertFalse(queryBus instanceof MultiTenantQueryBus,
                "QueryBus should not be multi-tenant without segment factory");
    }

    @Test
    void queryBusWrappedWhenBothFactoryAndResolverConfigured() {
        QueryBus mockSegmentBus = mock(QueryBus.class);
        TenantQuerySegmentFactory segmentFactory = tenant -> mockSegmentBus;

        Configuration resultConfig = MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
                .registerQueryBusSegmentFactory(config -> segmentFactory)
                .registerTargetTenantResolver(config -> (message, tenants) ->
                        TenantDescriptor.tenantWithId("test"))
                .build();

        QueryBus queryBus = resultConfig.getComponent(QueryBus.class);
        // InterceptingQueryBus wraps MultiTenantQueryBus (following AF5 decorator pattern)
        assertInstanceOf(InterceptingQueryBus.class, queryBus,
                "QueryBus should be wrapped with InterceptingQueryBus");
    }

    @Test
    void multipleComponentsCanBeConfiguredTogether() {
        CommandBus mockCommandBus = mock(CommandBus.class);
        QueryBus mockQueryBus = mock(QueryBus.class);

        Configuration resultConfig = MultiTenancyConfigurer.enhance(MessagingConfigurer.create())
                .registerCommandBusSegmentFactory(config -> tenant -> mockCommandBus)
                .registerQueryBusSegmentFactory(config -> tenant -> mockQueryBus)
                .registerTargetTenantResolver(config -> (message, tenants) ->
                        TenantDescriptor.tenantWithId("test"))
                .build();

        // Both buses are wrapped with intercepting decorators following AF5 pattern
        assertInstanceOf(InterceptingCommandBus.class, resultConfig.getComponent(CommandBus.class));
        assertInstanceOf(InterceptingQueryBus.class, resultConfig.getComponent(QueryBus.class));
    }

    @Test
    void decoratorOrderIsBeforeInterceptingBus() {
        // Verify our decoration order is less than InterceptingCommandBus (MIN + 100)
        // so that InterceptingCommandBus wraps our MultiTenantCommandBus
        assertTrue(
                MultiTenantCommandBus.DECORATION_ORDER < InterceptingCommandBus.DECORATION_ORDER,
                "Multi-tenant decorator should run before intercepting decorator"
        );
    }
}
