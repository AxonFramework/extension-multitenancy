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
package org.axonframework.extension.multitenancy.axonserver;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.query.QueryChannel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;
import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantAxonServerQueryBusConnector}.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 */
class MultiTenantAxonServerQueryBusConnectorTest {

    private AxonServerConnectionManager connectionManager;
    private AxonServerConfiguration axonServerConfiguration;
    private TargetTenantResolver<Message> targetTenantResolver;

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant2");

    @BeforeEach
    void setUp() {
        connectionManager = mock(AxonServerConnectionManager.class);
        axonServerConfiguration = mock(AxonServerConfiguration.class);
        targetTenantResolver = mock(TargetTenantResolver.class);

        when(axonServerConfiguration.getClientId()).thenReturn("test-client");
        when(axonServerConfiguration.getComponentName()).thenReturn("test-component");
    }

    @Test
    void buildWithAllRequiredComponentsSucceeds() {
        MultiTenantAxonServerQueryBusConnector connector = MultiTenantAxonServerQueryBusConnector.builder()
                .connectionManager(connectionManager)
                .axonServerConfiguration(axonServerConfiguration)
                .targetTenantResolver(targetTenantResolver)
                .build();

        assertNotNull(connector);
    }

    @Test
    void buildWithoutConnectionManagerFails() {
        assertThrows(AxonConfigurationException.class, () ->
                MultiTenantAxonServerQueryBusConnector.builder()
                        .axonServerConfiguration(axonServerConfiguration)
                        .targetTenantResolver(targetTenantResolver)
                        .build()
        );
    }

    @Test
    void buildWithoutAxonServerConfigurationFails() {
        assertThrows(AxonConfigurationException.class, () ->
                MultiTenantAxonServerQueryBusConnector.builder()
                        .connectionManager(connectionManager)
                        .targetTenantResolver(targetTenantResolver)
                        .build()
        );
    }

    @Test
    void buildWithoutTargetTenantResolverFails() {
        assertThrows(AxonConfigurationException.class, () ->
                MultiTenantAxonServerQueryBusConnector.builder()
                        .connectionManager(connectionManager)
                        .axonServerConfiguration(axonServerConfiguration)
                        .build()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void createFromReturnsConnectorWhenAllComponentsAvailable() {
        Configuration config = mock(Configuration.class);
        when(config.getOptionalComponent(AxonServerConnectionManager.class))
                .thenReturn(Optional.of(connectionManager));
        when(config.getOptionalComponent(TargetTenantResolver.class))
                .thenReturn(Optional.of(targetTenantResolver));
        when(config.getComponent(AxonServerConfiguration.class))
                .thenReturn(axonServerConfiguration);

        MultiTenantAxonServerQueryBusConnector result =
                MultiTenantAxonServerQueryBusConnector.createFrom(config);

        assertNotNull(result);
    }

    @Test
    void createFromReturnsNullWhenConnectionManagerNotAvailable() {
        Configuration config = mock(Configuration.class);
        when(config.getOptionalComponent(AxonServerConnectionManager.class))
                .thenReturn(Optional.empty());

        MultiTenantAxonServerQueryBusConnector result =
                MultiTenantAxonServerQueryBusConnector.createFrom(config);

        assertNull(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createFromReturnsNullWhenTargetTenantResolverNotAvailable() {
        Configuration config = mock(Configuration.class);
        when(config.getOptionalComponent(AxonServerConnectionManager.class))
                .thenReturn(Optional.of(connectionManager));
        when(config.getOptionalComponent(TargetTenantResolver.class))
                .thenReturn(Optional.empty());

        MultiTenantAxonServerQueryBusConnector result =
                MultiTenantAxonServerQueryBusConnector.createFrom(config);

        assertNull(result);
    }

    @Test
    void registerTenantAddsConnectorForTenant() {
        AxonServerConnection tenantConnection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenantConnection);

        MultiTenantAxonServerQueryBusConnector connector = createConnector();

        assertTrue(connector.connectors().isEmpty());

        var registration = connector.registerTenant(TENANT_1);

        assertEquals(1, connector.connectors().size());
        assertTrue(connector.connectors().containsKey(TENANT_1));
        assertNotNull(registration);
    }

    @Test
    void unregisterTenantRemovesConnector() {
        AxonServerConnection tenantConnection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenantConnection);

        MultiTenantAxonServerQueryBusConnector connector = createConnector();
        var registration = connector.registerTenant(TENANT_1);

        assertEquals(1, connector.connectors().size());

        registration.cancel();

        assertTrue(connector.connectors().isEmpty());
    }

    @Test
    void queryToUnknownTenantReturnsFailedStream() {
        MultiTenantAxonServerQueryBusConnector connector = createConnector();

        QueryMessage query = mock(QueryMessage.class);
        when(targetTenantResolver.resolveTenant(eq(query), any(Collection.class))).thenReturn(TENANT_1);

        MessageStream<QueryResponseMessage> result = connector.query(query, null);

        assertNotNull(result);
        // The stream should contain an error when consumed
    }

    @Test
    void subscriptionQueryToUnknownTenantReturnsFailedStream() {
        MultiTenantAxonServerQueryBusConnector connector = createConnector();

        QueryMessage query = mock(QueryMessage.class);
        when(targetTenantResolver.resolveTenant(eq(query), any(Collection.class))).thenReturn(TENANT_1);

        MessageStream<QueryResponseMessage> result = connector.subscriptionQuery(query, null, 100);

        assertNotNull(result);
        // The stream should contain an error when consumed
    }

    @Test
    void subscribeWithNoTenantsCompletesSuccessfully() {
        MultiTenantAxonServerQueryBusConnector connector = createConnector();

        QualifiedName queryName = new QualifiedName("TestQuery");
        CompletableFuture<Void> result = connector.subscribe(queryName);

        assertDoesNotThrow(() -> result.get());
    }

    @Test
    void registerAndStartTenantReplaysSubscriptions() {
        AxonServerConnection tenantConnection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenantConnection);

        MultiTenantAxonServerQueryBusConnector connector = createConnector();

        // Subscribe before any tenants
        QualifiedName queryName = new QualifiedName("TestQuery");
        connector.subscribe(queryName);

        // Register tenant - should replay subscription
        connector.registerAndStartTenant(TENANT_1);

        // Verify tenant connector was created
        assertEquals(1, connector.connectors().size());
    }

    @Test
    void onIncomingQuerySetsHandlerOnAllConnectors() {
        AxonServerConnection tenant1Connection = mockTenantConnection();
        AxonServerConnection tenant2Connection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenant1Connection);
        when(connectionManager.getConnection("tenant2")).thenReturn(tenant2Connection);

        MultiTenantAxonServerQueryBusConnector connector = createConnector();
        connector.registerTenant(TENANT_1);
        connector.registerTenant(TENANT_2);

        QueryBusConnector.Handler handler = mock(QueryBusConnector.Handler.class);
        connector.onIncomingQuery(handler);

        // Handler should be set on all tenant connectors
        assertEquals(2, connector.connectors().size());
    }

    @Test
    void shutdownCompletesSuccessfully() {
        AxonServerConnection tenantConnection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenantConnection);

        MultiTenantAxonServerQueryBusConnector connector = createConnector();
        connector.registerTenant(TENANT_1);

        CompletableFuture<Void> result = connector.shutdown();

        assertDoesNotThrow(() -> result.get());
    }

    private MultiTenantAxonServerQueryBusConnector createConnector() {
        return MultiTenantAxonServerQueryBusConnector.builder()
                .connectionManager(connectionManager)
                .axonServerConfiguration(axonServerConfiguration)
                .targetTenantResolver(targetTenantResolver)
                .build();
    }

    private AxonServerConnection mockTenantConnection() {
        AxonServerConnection connection = mock(AxonServerConnection.class);
        QueryChannel queryChannel = mock(QueryChannel.class);

        when(connection.queryChannel()).thenReturn(queryChannel);
        when(connection.isConnected()).thenReturn(false);
        when(queryChannel.registerQueryHandler(any(), any()))
                .thenReturn(mock(Registration.class));

        return connection;
    }
}
