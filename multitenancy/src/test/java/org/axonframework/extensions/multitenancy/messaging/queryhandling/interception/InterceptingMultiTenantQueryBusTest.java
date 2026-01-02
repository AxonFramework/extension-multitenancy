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

package org.axonframework.extensions.multitenancy.messaging.queryhandling.interception;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.messaging.queryhandling.MultiTenantQueryBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.interception.InterceptingQueryBus;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating that {@link InterceptingQueryBus} correctly wraps
 * {@link MultiTenantQueryBus}, ensuring interceptors are invoked before
 * tenant routing occurs.
 * <p>
 * This follows the same pattern as {@code InterceptingQueryBusTest} in the
 * core framework, but validates the multi-tenant decorator chain:
 * <pre>
 *     InterceptingQueryBus → MultiTenantQueryBus → TenantSegments
 * </pre>
 *
 * @author Stefan Dragisic
 * @since 5.0.0
 */
class InterceptingMultiTenantQueryBusTest {

    private static final MessageType TEST_QUERY_TYPE = new MessageType("TestQuery");
    private static final MessageType TEST_RESPONSE_TYPE = new MessageType(String.class);
    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private QueryBus tenantSegment1;
    private QueryBus tenantSegment2;
    private MultiTenantQueryBus multiTenantQueryBus;
    private InterceptingQueryBus testSubject;

    @BeforeEach
    void setUp() {
        tenantSegment1 = mock(QueryBus.class);
        tenantSegment2 = mock(QueryBus.class);

        // Configure mock segments to return success
        when(tenantSegment1.query(any(), any()))
                .thenReturn(MessageStream.just(
                        new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "success-tenant1")));
        when(tenantSegment2.query(any(), any()))
                .thenReturn(MessageStream.just(
                        new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "success-tenant2")));

        // Create MultiTenantQueryBus that routes to tenant2
        multiTenantQueryBus = MultiTenantQueryBus.builder()
                .tenantSegmentFactory(tenant -> {
                    if (TENANT_1.equals(tenant)) {
                        return tenantSegment1;
                    }
                    return tenantSegment2;
                })
                .targetTenantResolver((message, tenants) -> TENANT_2)
                .build();

        // Register tenants
        multiTenantQueryBus.registerTenant(TENANT_1);
        multiTenantQueryBus.registerTenant(TENANT_2);
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

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(trackingInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            testSubject.query(query, null);

            assertTrue(interceptorInvoked.get(), "Dispatch interceptor should be invoked");
            verify(tenantSegment2).query(any(), any());
        }

        @Test
        void dispatchInterceptorsCanModifyQueryBeforeRouting() {
            MessageDispatchInterceptor<Message> addMetadataInterceptor = (message, context, chain) -> {
                Message modified = message.andMetadata(Map.of("intercepted", "true"));
                return chain.proceed(modified, context);
            };

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(addMetadataInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            testSubject.query(query, null);

            // Verify the tenant segment received the modified query
            ArgumentCaptor<QueryMessage> queryCaptor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(tenantSegment2).query(queryCaptor.capture(), any());

            QueryMessage dispatchedQuery = queryCaptor.getValue();
            assertTrue(dispatchedQuery.metadata().containsKey("intercepted"),
                    "Query should contain interceptor-added metadata");
            assertEquals("true", dispatchedQuery.metadata().get("intercepted"));
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

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(firstInterceptor, secondInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            testSubject.query(query, null);

            ArgumentCaptor<QueryMessage> queryCaptor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(tenantSegment2).query(queryCaptor.capture(), any());

            QueryMessage dispatchedQuery = queryCaptor.getValue();
            assertTrue(dispatchedQuery.metadata().containsKey("first"),
                    "First interceptor metadata should be present");
            assertTrue(dispatchedQuery.metadata().containsKey("second"),
                    "Second interceptor metadata should be present");
        }

        @Test
        void interceptorCanShortCircuitQuery() {
            MessageDispatchInterceptor<Message> shortCircuitInterceptor = (message, context, chain) ->
                    MessageStream.failed(new RuntimeException("Short-circuited"));

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(shortCircuitInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            MessageStream<QueryResponseMessage> result = testSubject.query(query, null);

            assertTrue(result.first().asCompletableFuture().isCompletedExceptionally(),
                    "Result should be completed exceptionally when interceptor short-circuits");
            verify(tenantSegment1, never()).query(any(), any());
            verify(tenantSegment2, never()).query(any(), any());
        }
    }

    @Nested
    @DisplayName("Tenant routing tests")
    class TenantRoutingTests {

        @Test
        void queryIsRoutedToCorrectTenantAfterInterception() {
            MessageDispatchInterceptor<Message> loggingInterceptor = (message, context, chain) ->
                    chain.proceed(message, context);

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(loggingInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            testSubject.query(query, null);

            // Tenant resolver is configured to route to TENANT_2
            verify(tenantSegment2).query(any(), any());
            verify(tenantSegment1, never()).query(any(), any());
        }

        @Test
        void interceptedQueryReachesCorrectTenantSegment() {
            MessageDispatchInterceptor<Message> addTenantContextInterceptor = (message, context, chain) -> {
                Message modified = message.andMetadata(Map.of("tenant-context", "enriched"));
                return chain.proceed(modified, context);
            };

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(addTenantContextInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            MessageStream<QueryResponseMessage> result = testSubject.query(query, null);

            assertFalse(result.first().asCompletableFuture().isCompletedExceptionally());

            ArgumentCaptor<QueryMessage> queryCaptor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(tenantSegment2).query(queryCaptor.capture(), any());

            QueryMessage receivedQuery = queryCaptor.getValue();
            assertEquals("enriched", receivedQuery.metadata().get("tenant-context"),
                    "Tenant segment should receive enriched query");
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

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(resultTrackingInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            MessageStream<QueryResponseMessage> result = testSubject.query(query, null);

            // Consume the result to trigger the interceptor
            result.first().asCompletableFuture().join();

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

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(resultModifyingInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            MessageStream<QueryResponseMessage> result = testSubject.query(query, null);

            QueryResponseMessage responseMessage = result.first().asCompletableFuture().join().message();
            assertEquals("yes", responseMessage.metadata().get("result-enriched"),
                    "Result should contain interceptor-added metadata");
        }
    }

    @Nested
    @DisplayName("Subscription query tests")
    class SubscriptionQueryTests {

        @Test
        void subscriptionQueryInterceptorsAreInvokedBeforeTenantRouting() {
            AtomicBoolean interceptorInvoked = new AtomicBoolean(false);

            // Configure mock for subscription query
            when(tenantSegment2.subscriptionQuery(any(), any(), anyInt()))
                    .thenReturn(MessageStream.just(
                            new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "subscription-success")));

            MessageDispatchInterceptor<Message> trackingInterceptor = (message, context, chain) -> {
                interceptorInvoked.set(true);
                return chain.proceed(message, context);
            };

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(trackingInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            testSubject.subscriptionQuery(query, null, 10);

            assertTrue(interceptorInvoked.get(), "Dispatch interceptor should be invoked for subscription query");
            verify(tenantSegment2).subscriptionQuery(any(), any(), anyInt());
        }

        @Test
        void subscriptionQueryModifiedByInterceptorReachesCorrectTenant() {
            // Configure mock for subscription query
            when(tenantSegment2.subscriptionQuery(any(), any(), anyInt()))
                    .thenReturn(MessageStream.just(
                            new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "subscription-success")));

            MessageDispatchInterceptor<Message> addMetadataInterceptor = (message, context, chain) -> {
                Message modified = message.andMetadata(Map.of("subscription-context", "enriched"));
                return chain.proceed(modified, context);
            };

            testSubject = new InterceptingQueryBus(
                    multiTenantQueryBus,
                    List.of(),
                    List.of(addMetadataInterceptor),
                    List.of()
            );

            QueryMessage query = new GenericQueryMessage(TEST_QUERY_TYPE, "test-payload");
            testSubject.subscriptionQuery(query, null, 10);

            ArgumentCaptor<QueryMessage> queryCaptor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(tenantSegment2).subscriptionQuery(queryCaptor.capture(), any(), anyInt());

            QueryMessage dispatchedQuery = queryCaptor.getValue();
            assertEquals("enriched", dispatchedQuery.metadata().get("subscription-context"),
                    "Subscription query should contain interceptor-added metadata");
        }
    }
}
