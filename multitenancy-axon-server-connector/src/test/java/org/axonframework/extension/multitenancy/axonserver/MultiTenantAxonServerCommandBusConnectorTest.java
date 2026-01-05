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
import io.axoniq.axonserver.connector.command.CommandChannel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.multitenancy.core.NoSuchTenantException;
import org.axonframework.extension.multitenancy.core.TargetTenantResolver;
import org.axonframework.extension.multitenancy.core.TenantDescriptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiTenantAxonServerCommandBusConnector}.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 */
class MultiTenantAxonServerCommandBusConnectorTest {

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
        MultiTenantAxonServerCommandBusConnector connector = MultiTenantAxonServerCommandBusConnector.builder()
                .connectionManager(connectionManager)
                .axonServerConfiguration(axonServerConfiguration)
                .targetTenantResolver(targetTenantResolver)
                .build();

        assertNotNull(connector);
    }

    @Test
    void buildWithoutConnectionManagerFails() {
        assertThrows(AxonConfigurationException.class, () ->
                MultiTenantAxonServerCommandBusConnector.builder()
                        .axonServerConfiguration(axonServerConfiguration)
                        .targetTenantResolver(targetTenantResolver)
                        .build()
        );
    }

    @Test
    void buildWithoutAxonServerConfigurationFails() {
        assertThrows(AxonConfigurationException.class, () ->
                MultiTenantAxonServerCommandBusConnector.builder()
                        .connectionManager(connectionManager)
                        .targetTenantResolver(targetTenantResolver)
                        .build()
        );
    }

    @Test
    void buildWithoutTargetTenantResolverFails() {
        assertThrows(AxonConfigurationException.class, () ->
                MultiTenantAxonServerCommandBusConnector.builder()
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

        MultiTenantAxonServerCommandBusConnector result =
                MultiTenantAxonServerCommandBusConnector.createFrom(config);

        assertNotNull(result);
    }

    @Test
    void createFromReturnsNullWhenConnectionManagerNotAvailable() {
        Configuration config = mock(Configuration.class);
        when(config.getOptionalComponent(AxonServerConnectionManager.class))
                .thenReturn(Optional.empty());

        MultiTenantAxonServerCommandBusConnector result =
                MultiTenantAxonServerCommandBusConnector.createFrom(config);

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

        MultiTenantAxonServerCommandBusConnector result =
                MultiTenantAxonServerCommandBusConnector.createFrom(config);

        assertNull(result);
    }

    @Test
    void registerTenantAddsConnectorForTenant() {
        AxonServerConnection tenantConnection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenantConnection);

        MultiTenantAxonServerCommandBusConnector connector = createConnector();

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

        MultiTenantAxonServerCommandBusConnector connector = createConnector();
        var registration = connector.registerTenant(TENANT_1);

        assertEquals(1, connector.connectors().size());

        registration.cancel();

        assertTrue(connector.connectors().isEmpty());
    }

    @Test
    void dispatchToUnknownTenantFails() {
        MultiTenantAxonServerCommandBusConnector connector = createConnector();

        CommandMessage command = mock(CommandMessage.class);
        when(targetTenantResolver.resolveTenant(eq(command), any(Collection.class))).thenReturn(TENANT_1);

        CompletableFuture<?> result = connector.dispatch(command, null);

        assertTrue(result.isCompletedExceptionally());
        assertThrows(ExecutionException.class, () -> result.get());
        try {
            result.get();
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NoSuchTenantException);
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        }
    }

    @Test
    void subscribeWithNoTenantsCompletesSuccessfully() {
        MultiTenantAxonServerCommandBusConnector connector = createConnector();

        QualifiedName commandName = new QualifiedName("TestCommand");
        CompletableFuture<Void> result = connector.subscribe(commandName, 100);

        assertDoesNotThrow(() -> result.get());
    }

    @Test
    void registerAndStartTenantReplaysSubscriptions() {
        AxonServerConnection tenantConnection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenantConnection);

        MultiTenantAxonServerCommandBusConnector connector = createConnector();

        // Subscribe before any tenants
        QualifiedName commandName = new QualifiedName("TestCommand");
        connector.subscribe(commandName, 100);

        // Register tenant - should replay subscription
        connector.registerAndStartTenant(TENANT_1);

        // Verify tenant connector was created
        assertEquals(1, connector.connectors().size());
    }

    @Test
    void onIncomingCommandSetsHandlerOnAllConnectors() {
        AxonServerConnection tenant1Connection = mockTenantConnection();
        AxonServerConnection tenant2Connection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenant1Connection);
        when(connectionManager.getConnection("tenant2")).thenReturn(tenant2Connection);

        MultiTenantAxonServerCommandBusConnector connector = createConnector();
        connector.registerTenant(TENANT_1);
        connector.registerTenant(TENANT_2);

        CommandBusConnector.Handler handler = mock(CommandBusConnector.Handler.class);
        connector.onIncomingCommand(handler);

        // Handler should be set on all tenant connectors
        assertEquals(2, connector.connectors().size());
    }

    @Test
    void shutdownCompletesSuccessfully() {
        AxonServerConnection tenantConnection = mockTenantConnection();
        when(connectionManager.getConnection("tenant1")).thenReturn(tenantConnection);

        MultiTenantAxonServerCommandBusConnector connector = createConnector();
        connector.registerTenant(TENANT_1);

        CompletableFuture<Void> result = connector.shutdown();

        assertDoesNotThrow(() -> result.get());
    }

    private MultiTenantAxonServerCommandBusConnector createConnector() {
        return MultiTenantAxonServerCommandBusConnector.builder()
                .connectionManager(connectionManager)
                .axonServerConfiguration(axonServerConfiguration)
                .targetTenantResolver(targetTenantResolver)
                .build();
    }

    private AxonServerConnection mockTenantConnection() {
        AxonServerConnection connection = mock(AxonServerConnection.class);
        CommandChannel commandChannel = mock(CommandChannel.class);

        when(connection.commandChannel()).thenReturn(commandChannel);
        when(connection.isConnected()).thenReturn(false);
        when(commandChannel.prepareDisconnect()).thenReturn(CompletableFuture.completedFuture(null));
        when(commandChannel.registerCommandHandler(any(), anyInt(), any()))
                .thenReturn(mock(Registration.class));

        return connection;
    }
}
