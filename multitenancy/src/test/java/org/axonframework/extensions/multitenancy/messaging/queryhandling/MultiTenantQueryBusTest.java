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

package org.axonframework.extensions.multitenancy.messaging.queryhandling;

import org.axonframework.extensions.multitenancy.core.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantQueryBus}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantQueryBusTest {

    private static final String PAYLOAD = "testQuery";
    private static final MessageType QUERY_TYPE = new MessageType("TestQuery");
    private static final MessageType RESPONSE_TYPE = new MessageType("Response");
    private static final QueryMessage TEST_QUERY = new GenericQueryMessage(QUERY_TYPE, PAYLOAD);
    private static final QualifiedName QUERY_NAME = TEST_QUERY.type().qualifiedName();

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    private QueryBus tenantSegment1;
    private QueryBus tenantSegment2;

    private MultiTenantQueryBus testSubject;

    @BeforeEach
    void setUp() {
        tenantSegment1 = mock(QueryBus.class);
        tenantSegment2 = mock(QueryBus.class);

        // Stub subscribe to return the bus for fluent chaining
        when(tenantSegment1.subscribe(any(QualifiedName.class), any(QueryHandler.class))).thenReturn(tenantSegment1);
        when(tenantSegment2.subscribe(any(QualifiedName.class), any(QueryHandler.class))).thenReturn(tenantSegment2);

        TenantQuerySegmentFactory tenantSegmentFactory = tenant -> {
            if (tenant.tenantId().equals("tenant1")) {
                return tenantSegment1;
            } else {
                return tenantSegment2;
            }
        };

        // Resolver always resolves to tenant2
        TargetTenantResolver<Message> targetTenantResolver =
                (message, tenants) -> TENANT_2;

        testSubject = MultiTenantQueryBus.builder()
                                         .tenantSegmentFactory(tenantSegmentFactory)
                                         .targetTenantResolver(targetTenantResolver)
                                         .build();
    }

    @Test
    void queryRoutesToCorrectTenant() {
        QueryResponseMessage expectedResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, "result");
        when(tenantSegment2.query(any(), any()))
                .thenReturn(MessageStream.just(expectedResponse));

        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        MessageStream<QueryResponseMessage> result = testSubject.query(TEST_QUERY, null);

        verify(tenantSegment2).query(eq(TEST_QUERY), isNull());
        verify(tenantSegment1, never()).query(any(), any());
        assertNotNull(result);
    }

    @Test
    void queryToUnknownTenantReturnsFailedStream() {
        // No tenants registered
        MessageStream<QueryResponseMessage> result = testSubject.query(TEST_QUERY, null);

        // The stream should be failed - check via the error() method
        assertNotNull(result);
        assertTrue(result.error().isPresent());
    }

    @Test
    void subscriptionQueryRoutesToCorrectTenant() {
        QueryResponseMessage expectedResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, "result");
        when(tenantSegment2.subscriptionQuery(any(), any(), anyInt()))
                .thenReturn(MessageStream.just(expectedResponse));

        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        MessageStream<QueryResponseMessage> result = testSubject.subscriptionQuery(TEST_QUERY, null, 10);

        verify(tenantSegment2).subscriptionQuery(eq(TEST_QUERY), isNull(), eq(10));
        verify(tenantSegment1, never()).subscriptionQuery(any(), any(), anyInt());
    }

    @Test
    void subscribeRegistersHandlerOnExistingTenants() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        QueryHandler handler = (msg, ctx) -> MessageStream.just(new GenericQueryResponseMessage(RESPONSE_TYPE, "ok"));
        testSubject.subscribe(QUERY_NAME, handler);

        verify(tenantSegment1).subscribe(eq(QUERY_NAME), eq(handler));
        verify(tenantSegment2).subscribe(eq(QUERY_NAME), eq(handler));
    }

    @Test
    void registerAndStartTenantSubscribesExistingHandlers() {
        // First subscribe a handler
        QueryHandler handler = (msg, ctx) -> MessageStream.just(new GenericQueryResponseMessage(RESPONSE_TYPE, "ok"));
        testSubject.subscribe(QUERY_NAME, handler);

        // Then register and start a tenant
        testSubject.registerAndStartTenant(TENANT_1);

        // Handler should be subscribed to the new tenant segment
        verify(tenantSegment1).subscribe(eq(QUERY_NAME), eq(handler));
    }

    @Test
    void emitUpdateRoutesToCorrectTenant() {
        when(tenantSegment2.emitUpdate(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        // Create a mock ProcessingContext that returns our test query
        ProcessingContext context = mock(ProcessingContext.class);
        when(context.getResource(Message.RESOURCE_KEY)).thenReturn(TEST_QUERY);

        testSubject.emitUpdate(q -> true, () -> null, context);

        // Should only emit to tenant2 (resolved from the message)
        verify(tenantSegment2).emitUpdate(any(), any(), any());
        verify(tenantSegment1, never()).emitUpdate(any(), any(), any());
    }

    @Test
    void emitUpdateWithoutContextReturnsFailedFuture() {
        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        CompletableFuture<Void> result = testSubject.emitUpdate(q -> true, () -> null, null);

        assertTrue(result.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void completeSubscriptionsRoutesToCorrectTenant() {
        when(tenantSegment2.completeSubscriptions(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        testSubject.registerTenant(TENANT_1);
        testSubject.registerTenant(TENANT_2);

        // Create a mock ProcessingContext that returns our test query
        ProcessingContext context = mock(ProcessingContext.class);
        when(context.getResource(Message.RESOURCE_KEY)).thenReturn(TEST_QUERY);

        testSubject.completeSubscriptions(q -> true, context);

        // Should only complete on tenant2 (resolved from the message)
        verify(tenantSegment2).completeSubscriptions(any(), any());
        verify(tenantSegment1, never()).completeSubscriptions(any(), any());
    }

    @Test
    void unregisterTenantRemovesTenantFromRouting() {
        testSubject.registerTenant(TENANT_2);

        // Unregister the tenant
        testSubject.registerTenant(TENANT_2).cancel();

        // Should fail because tenant is no longer registered
        MessageStream<QueryResponseMessage> result = testSubject.query(TEST_QUERY, null);
        assertTrue(result.error().isPresent());
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
                MultiTenantQueryBus.builder()
                                   .targetTenantResolver((m, t) -> TENANT_1)
                                   .build()
        );
    }

    @Test
    void builderRequiresTargetTenantResolver() {
        assertThrows(Exception.class, () ->
                MultiTenantQueryBus.builder()
                                   .tenantSegmentFactory(t -> tenantSegment1)
                                   .build()
        );
    }
}
