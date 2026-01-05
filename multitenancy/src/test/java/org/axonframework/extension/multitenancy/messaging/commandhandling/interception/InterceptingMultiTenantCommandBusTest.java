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

package org.axonframework.extension.multitenancy.messaging.commandhandling.interception;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.extension.multitenancy.messaging.commandhandling.MultiTenantCommandBus;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.interception.InterceptingCommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating that {@link InterceptingCommandBus} correctly wraps
 * {@link MultiTenantCommandBus}, ensuring interceptors are invoked before
 * tenant routing occurs.
 * <p>
 * This follows the same pattern as {@code InterceptingCommandBusTest} in the
 * core framework, but validates the multi-tenant decorator chain:
 * <pre>
 *     InterceptingCommandBus → MultiTenantCommandBus → TenantSegments
 * </pre>
 *
 * @author Stefan Dragisic
 * @since 5.0.0
 */
class InterceptingMultiTenantCommandBusTest {

    private static final MessageType TEST_COMMAND_TYPE = new MessageType("TestCommand");
    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private CommandBus tenantSegment1;
    private CommandBus tenantSegment2;
    private MultiTenantCommandBus multiTenantCommandBus;
    private InterceptingCommandBus testSubject;

    @BeforeEach
    void setUp() {
        tenantSegment1 = mock(CommandBus.class);
        tenantSegment2 = mock(CommandBus.class);

        // Configure mock segments to return success
        when(tenantSegment1.dispatch(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GenericCommandResultMessage(TEST_COMMAND_TYPE, "success-tenant1")));
        when(tenantSegment2.dispatch(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GenericCommandResultMessage(TEST_COMMAND_TYPE, "success-tenant2")));

        // Create MultiTenantCommandBus that routes to tenant2
        multiTenantCommandBus = MultiTenantCommandBus.builder()
                .tenantSegmentFactory(tenant -> {
                    if (TENANT_1.equals(tenant)) {
                        return tenantSegment1;
                    }
                    return tenantSegment2;
                })
                .targetTenantResolver((message, tenants) -> TENANT_2)
                .build();

        // Register tenants
        multiTenantCommandBus.registerTenant(TENANT_1);
        multiTenantCommandBus.registerTenant(TENANT_2);
    }

    @Nested
    @DisplayName("Dispatch interceptor tests")
    class DispatchInterceptorTests {

        @Test
        void dispatchInterceptorsAreInvokedBeforeTenantRouting() {
            AtomicBoolean interceptorInvoked = new AtomicBoolean(false);

            MessageDispatchInterceptor<Message> trackingInterceptor = (message, context, chain) -> {
                interceptorInvoked.set(true);
                return chain.proceed(message, context);
            };

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(trackingInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            testSubject.dispatch(command, null);

            assertTrue(interceptorInvoked.get(), "Dispatch interceptor should be invoked");
            verify(tenantSegment2).dispatch(any(), any());
        }

        @Test
        void dispatchInterceptorsCanModifyCommandBeforeRouting() {
            MessageDispatchInterceptor<Message> addMetadataInterceptor = (message, context, chain) -> {
                Message modified = message.andMetadata(Map.of("intercepted", "true"));
                return chain.proceed(modified, context);
            };

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(addMetadataInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            testSubject.dispatch(command, null);

            // Verify the tenant segment received the modified command
            ArgumentCaptor<CommandMessage> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(tenantSegment2).dispatch(commandCaptor.capture(), any());

            CommandMessage dispatchedCommand = commandCaptor.getValue();
            assertTrue(dispatchedCommand.metadata().containsKey("intercepted"),
                    "Command should contain interceptor-added metadata");
            assertEquals("true", dispatchedCommand.metadata().get("intercepted"));
        }

        @Test
        void multipleDispatchInterceptorsAreInvokedInOrder() {
            MessageDispatchInterceptor<Message> firstInterceptor = (message, context, chain) -> {
                Message modified = message.andMetadata(Map.of("first", "1"));
                return chain.proceed(modified, context);
            };

            MessageDispatchInterceptor<Message> secondInterceptor = (message, context, chain) -> {
                Message modified = message.andMetadata(Map.of("second", "2"));
                return chain.proceed(modified, context);
            };

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(firstInterceptor, secondInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            testSubject.dispatch(command, null);

            ArgumentCaptor<CommandMessage> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(tenantSegment2).dispatch(commandCaptor.capture(), any());

            CommandMessage dispatchedCommand = commandCaptor.getValue();
            assertTrue(dispatchedCommand.metadata().containsKey("first"),
                    "First interceptor metadata should be present");
            assertTrue(dispatchedCommand.metadata().containsKey("second"),
                    "Second interceptor metadata should be present");
        }

        @Test
        void interceptorCanShortCircuitDispatch() {
            MessageDispatchInterceptor<Message> shortCircuitInterceptor = (message, context, chain) ->
                    MessageStream.failed(new RuntimeException("Short-circuited"));

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(shortCircuitInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            CompletableFuture<CommandResultMessage> result = testSubject.dispatch(command, null);

            assertTrue(result.isCompletedExceptionally(),
                    "Result should be completed exceptionally when interceptor short-circuits");
            verify(tenantSegment1, never()).dispatch(any(), any());
            verify(tenantSegment2, never()).dispatch(any(), any());
        }
    }

    @Nested
    @DisplayName("Tenant routing tests")
    class TenantRoutingTests {

        @Test
        void commandIsRoutedToCorrectTenantAfterInterception() {
            MessageDispatchInterceptor<Message> loggingInterceptor = (message, context, chain) ->
                    chain.proceed(message, context);

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(loggingInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            testSubject.dispatch(command, null);

            // Tenant resolver is configured to route to TENANT_2
            verify(tenantSegment2).dispatch(any(), any());
            verify(tenantSegment1, never()).dispatch(any(), any());
        }

        @Test
        void interceptedCommandReachesCorrectTenantSegment() {
            MessageDispatchInterceptor<Message> addTenantContextInterceptor = (message, context, chain) -> {
                Message modified = message.andMetadata(Map.of("tenant-context", "enriched"));
                return chain.proceed(modified, context);
            };

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(addTenantContextInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            CompletableFuture<CommandResultMessage> result = testSubject.dispatch(command, null);

            assertTrue(result.isDone());
            assertFalse(result.isCompletedExceptionally());

            ArgumentCaptor<CommandMessage> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(tenantSegment2).dispatch(commandCaptor.capture(), any());

            CommandMessage receivedCommand = commandCaptor.getValue();
            assertEquals("enriched", receivedCommand.metadata().get("tenant-context"),
                    "Tenant segment should receive enriched command");
        }
    }

    @Nested
    @DisplayName("Result handling tests")
    class ResultHandlingTests {

        @Test
        void resultFromTenantSegmentIsReturnedThroughInterceptorChain() {
            AtomicBoolean resultIntercepted = new AtomicBoolean(false);

            MessageDispatchInterceptor<Message> resultTrackingInterceptor =
                    new MessageDispatchInterceptor<>() {
                        @Nonnull
                        @Override
                        public MessageStream<?> interceptOnDispatch(@Nonnull Message message,
                                                                    @Nullable ProcessingContext context,
                                                                    @Nonnull MessageDispatchInterceptorChain<Message> chain) {
                            return chain.proceed(message, context)
                                    .onNext(m -> resultIntercepted.set(true));
                        }
                    };

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(resultTrackingInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            CompletableFuture<CommandResultMessage> result = testSubject.dispatch(command, null);

            assertTrue(result.isDone());
            assertTrue(resultIntercepted.get(), "Result should pass back through interceptor chain");
        }

        @Test
        void interceptorCanModifyResult() {
            MessageDispatchInterceptor<Message> resultModifyingInterceptor =
                    new MessageDispatchInterceptor<>() {
                        @Nonnull
                        @Override
                        public MessageStream<?> interceptOnDispatch(@Nonnull Message message,
                                                                    @Nullable ProcessingContext context,
                                                                    @Nonnull MessageDispatchInterceptorChain<Message> chain) {
                            return chain.proceed(message, context)
                                    .mapMessage(m -> m.andMetadata(Map.of("result-enriched", "yes")));
                        }
                    };

            testSubject = new InterceptingCommandBus(
                    multiTenantCommandBus,
                    List.of(),
                    List.of(resultModifyingInterceptor)
            );

            CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "test-payload");
            CompletableFuture<CommandResultMessage> result = testSubject.dispatch(command, null);

            assertTrue(result.isDone());
            CommandResultMessage resultMessage = result.join();
            assertEquals("yes", resultMessage.metadata().get("result-enriched"),
                    "Result should contain interceptor-added metadata");
        }
    }
}
