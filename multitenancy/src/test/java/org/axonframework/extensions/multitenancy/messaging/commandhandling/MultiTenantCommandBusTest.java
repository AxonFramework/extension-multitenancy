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

package org.axonframework.extensions.multitenancy.messaging.commandhandling;

import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantCommandBus}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantCommandBusTest {

    private static final String PAYLOAD = "testCommand";
    private static final MessageType COMMAND_TYPE = new MessageType("TestCommand");
    private static final CommandMessage TEST_COMMAND = new GenericCommandMessage(COMMAND_TYPE, PAYLOAD);
    private static final QualifiedName COMMAND_NAME = TEST_COMMAND.type().qualifiedName();

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private CommandBus tenantSegment1;
    private CommandBus tenantSegment2;

    private MultiTenantCommandBus testSubject;

    @BeforeEach
    void setUp() {
        tenantSegment1 = mock(CommandBus.class);
        tenantSegment2 = mock(CommandBus.class);

        // Stub subscribe to return the bus for fluent chaining
        when(tenantSegment1.subscribe(any(QualifiedName.class), any(CommandHandler.class))).thenReturn(tenantSegment1);
        when(tenantSegment2.subscribe(any(QualifiedName.class), any(CommandHandler.class))).thenReturn(tenantSegment2);

        TenantCommandSegmentFactory tenantSegmentFactory = tenant -> {
            if (tenant.tenantId().equals("tenant1")) {
                return tenantSegment1;
            } else {
                return tenantSegment2;
            }
        };

        // Resolver always resolves to tenant2
        TargetTenantResolver<Message> targetTenantResolver =
                (message, tenants) -> TENANT_2;

        testSubject = MultiTenantCommandBus.builder()
                                           .tenantSegmentFactory(tenantSegmentFactory)
                                           .targetTenantResolver(targetTenantResolver)
                                           .build();
    }

    @Test
    void dispatchRoutesToCorrectTenant() {
        CommandResultMessage expectedResult = new GenericCommandResultMessage(COMMAND_TYPE, "result");
        when(tenantSegment2.dispatch(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedResult));

        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        CompletableFuture<CommandResultMessage> result = testSubject.dispatch(TEST_COMMAND, null);

        verify(tenantSegment2).dispatch(eq(TEST_COMMAND), isNull());
        verify(tenantSegment1, never()).dispatch(any(), any());
        assertTrue(result.isDone());
    }

    @Test
    void dispatchToUnknownTenantReturnsFailedFuture() {
        // No tenants registered
        CompletableFuture<CommandResultMessage> result = testSubject.dispatch(TEST_COMMAND, null);

        assertTrue(result.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(NoSuchTenantException.class, exception.getCause());
        assertEquals("Tenant with identifier [tenant2] is unknown", exception.getCause().getMessage());
    }

    @Test
    void subscribeRegistersHandlerOnExistingTenants() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        CommandHandler handler = (msg, ctx) -> MessageStream.just(new GenericCommandResultMessage(COMMAND_TYPE, "ok"));
        testSubject.subscribe(COMMAND_NAME, handler);

        verify(tenantSegment1).subscribe(eq(COMMAND_NAME), eq(handler));
        verify(tenantSegment2).subscribe(eq(COMMAND_NAME), eq(handler));
    }

    @Test
    void registerAndStartTenantSubscribesExistingHandlers() {
        // First subscribe a handler
        CommandHandler handler = (msg, ctx) -> MessageStream.just(new GenericCommandResultMessage(COMMAND_TYPE, "ok"));
        testSubject.subscribe(COMMAND_NAME, handler);

        // Then register and start a tenant
        testSubject.registerAndStartTenant(TENANT_1);

        // Handler should be subscribed to the new tenant segment
        verify(tenantSegment1).subscribe(eq(COMMAND_NAME), eq(handler));
    }

    @Test
    void unregisterTenantRemovesTenantFromRouting() {
        testSubject.registerTenant(TENANT_2);

        // Unregister the tenant
        testSubject.registerTenant(TENANT_2).cancel();

        // Should fail because tenant is no longer registered
        CompletableFuture<CommandResultMessage> result = testSubject.dispatch(TEST_COMMAND, null);

        assertTrue(result.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(NoSuchTenantException.class, exception.getCause());
    }

    @Test
    void tenantSegmentsReturnsRegisteredTenants() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        assertEquals(2, testSubject.tenantSegments().size());
        assertTrue(testSubject.tenantSegments().containsKey(TENANT_1));
        assertTrue(testSubject.tenantSegments().containsKey(TENANT_2));
    }

    @Test
    void builderRequiresTenantSegmentFactory() {
        assertThrows(Exception.class, () ->
                MultiTenantCommandBus.builder()
                                     .targetTenantResolver((m, t) -> TENANT_1)
                                     .build()
        );
    }

    @Test
    void builderRequiresTargetTenantResolver() {
        assertThrows(Exception.class, () ->
                MultiTenantCommandBus.builder()
                                     .tenantSegmentFactory(t -> tenantSegment1)
                                     .build()
        );
    }
}
