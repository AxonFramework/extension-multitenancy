/*
 * Copyright (c) 2010-2023. Axon Framework
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

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.grpc.admin.ContextOverview;
import io.axoniq.axonserver.grpc.admin.ContextUpdate;
import io.axoniq.axonserver.grpc.admin.ContextUpdateType;
import io.axoniq.axonserver.grpc.admin.ReplicationGroupOverview;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class AxonServerTenantProviderTest {

    private AxonServerTenantProvider testSubject;
    private TenantConnectPredicate tenantConnectPredicate;
    private AxonServerConnectionManager axonServerConnectionManager;

    @BeforeEach
    void setUp() {
        tenantConnectPredicate = ctx -> ctx.tenantId().startsWith("tenant");
        axonServerConnectionManager = mock(AxonServerConnectionManager.class);

        AxonServerConnection axonServerConnection = mock(AxonServerConnection.class);
        when(axonServerConnectionManager.getConnection(anyString())).thenReturn(axonServerConnection);
        AdminChannel adminChannel = mock(AdminChannel.class);
        when(axonServerConnection.adminChannel()).thenReturn(adminChannel);
        ResultStream<ContextUpdate> updatesStream = new StubResultStream<>(ContextUpdate.newBuilder()
                                                                                        .setContext("default")
                                                                                        .setType(ContextUpdateType.CREATED)
                                                                                        .build(),
                                                                           ContextUpdate.newBuilder()
                                                                                        .setContext("tenant-1")
                                                                                        .setType(ContextUpdateType.CREATED)
                                                                                        .build(),
                                                                           ContextUpdate.newBuilder()
                                                                                        .setContext("tenant-2")
                                                                                        .setType(ContextUpdateType.CREATED)
                                                                                        .build(),
                                                                           ContextUpdate.newBuilder()
                                                                                        .setContext("tenant-2")
                                                                                        .setType(ContextUpdateType.DELETED)
                                                                                        .build());
        when(adminChannel.subscribeToContextUpdates()).thenReturn(updatesStream);

        ArgumentCaptor<String> contextName = ArgumentCaptor.forClass(String.class);
        when(adminChannel.getContextOverview(contextName.capture()))
                .thenAnswer(unused -> CompletableFuture.completedFuture(ContextOverview.newBuilder()
                                                                                       .setName(contextName.getValue())
                                                                                       .setChangePending(true)
                                                                                       .setPendingSince(1000L)
                                                                                       .setReplicationGroup(
                                                                                               ReplicationGroupOverview.newBuilder()
                                                                                                                       .setName(
                                                                                                                               "default-rp")
                                                                                                                       .build())
                                                                                       .build()));

        when(adminChannel.getAllContexts())
                .thenReturn(CompletableFuture.completedFuture(Arrays.asList(
                        ContextOverview.newBuilder()
                                       .setName("tenant-3")
                                       .setChangePending(true)
                                       .setPendingSince(1000L)
                                       .setReplicationGroup(
                                               ReplicationGroupOverview.newBuilder()
                                                                       .setName(
                                                                               "tenant-3-rp")
                                                                       .build())
                                       .build(),
                        ContextOverview.newBuilder()
                                       .setName("tenant-4")
                                       .setChangePending(false)
                                       .setPendingSince(2000L)
                                       .setReplicationGroup(
                                               ReplicationGroupOverview.newBuilder()
                                                                       .setName(
                                                                               "tenant-4-rp")
                                                                       .build())
                                       .build()
                )));
    }


    @Test
    void whenInitialTenantsThenStart() {
        testSubject = new AxonServerTenantProvider("default, tenant-1, tenant-2",
                                                   tenantConnectPredicate,
                                                   axonServerConnectionManager);
        MultiTenantAwareComponent mockComponent = mock(MultiTenantAwareComponent.class);
        when(mockComponent.registerTenant(any())).thenReturn(() -> true);

        //first start provider
        testSubject.start();

        //add new component
        testSubject.subscribe(mockComponent);

        //noinspection resource
        verify(mockComponent).registerTenant(TenantDescriptor.tenantWithId("tenant-1"));
        //noinspection resource
        verify(mockComponent).registerTenant(TenantDescriptor.tenantWithId("tenant-2"));
    }

    @Test
    void whenInitialTenantsIsEmpty() throws InterruptedException {
        testSubject = new AxonServerTenantProvider("", tenantConnectPredicate, axonServerConnectionManager);
        MultiTenantAwareComponent mockComponent = mock(MultiTenantAwareComponent.class);

        //first start provider
        testSubject.start();

        //add new component
        testSubject.subscribe(mockComponent);

        Thread.sleep(3000);

        ArgumentCaptor<TenantDescriptor> tenantCaptor = ArgumentCaptor.forClass(TenantDescriptor.class);
        //initial setup
        //noinspection resource
        verify(mockComponent, times(2)).registerTenant(tenantCaptor.capture());
        tenantCaptor.getAllValues().forEach(tenantDescriptor -> {
            if (tenantDescriptor.tenantId().equals("tenant-3")) {
                assertEquals("tenant-3", tenantDescriptor.tenantId());
                assertEquals("tenant-3-rp", tenantDescriptor.properties().get("replicationGroup"));
            } else if (tenantDescriptor.tenantId().equals("tenant-4")) {
                assertEquals("tenant-4", tenantDescriptor.tenantId());
                assertEquals("tenant-4-rp", tenantDescriptor.properties().get("replicationGroup"));
            } else {
                fail("Unexpected tenant descriptor");
            }
        });

        tenantCaptor = ArgumentCaptor.forClass(TenantDescriptor.class);
        //additionally created contexts
        verify(mockComponent, times(2)).registerAndStartTenant(tenantCaptor.capture());
        List<TenantDescriptor> resultDescriptors = tenantCaptor.getAllValues();
        assertEquals(2, resultDescriptors.size());
        assertEquals("tenant-1", resultDescriptors.get(0).tenantId());
        assertEquals("tenant-2", resultDescriptors.get(1).tenantId());
    }

    private static class StubResultStream<T> implements ResultStream<T> {

        private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
        private final Iterator<T> responses;
        private final Throwable error;
        private T peeked;
        private volatile boolean closed;
        private final int totalNumberOfElements;

        @SafeVarargs
        public StubResultStream(T... responses) {
            this.error = null;
            List<T> queryResponses = asList(responses);
            this.responses = queryResponses.iterator();
            this.totalNumberOfElements = queryResponses.size();
            this.closed = totalNumberOfElements == 0;
        }

        @Override
        public T peek() {
            if (peeked == null && responses.hasNext()) {
                peeked = responses.next();
            }
            return peeked;
        }

        @Override
        public T nextIfAvailable() {
            if (peeked != null) {
                T result = peeked;
                peeked = null;
                closeIfThereAreNoMoreElements();
                return result;
            }
            if (responses.hasNext()) {
                T next = responses.next();
                closeIfThereAreNoMoreElements();
                return next;
            } else {
                return null;
            }
        }

        private void closeIfThereAreNoMoreElements() {
            if (!responses.hasNext()) {
                close();
            }
        }

        @Override
        public T nextIfAvailable(long timeout, TimeUnit unit) {
            return nextIfAvailable();
        }

        @Override
        public T next() {
            return nextIfAvailable();
        }

        AtomicInteger messageNumber = new AtomicInteger(0);

        @Override
        public void onAvailable(Runnable r) {
            if (peeked != null || responses.hasNext() || isClosed()) {
                IntStream.rangeClosed(0, totalNumberOfElements)
                         .forEach(i -> executor.schedule(r,
                                                         1000 + messageNumber.getAndIncrement() * 100L,
                                                         TimeUnit.MILLISECONDS));
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Optional<Throwable> getError() {
            return Optional.ofNullable(error);
        }
    }
}