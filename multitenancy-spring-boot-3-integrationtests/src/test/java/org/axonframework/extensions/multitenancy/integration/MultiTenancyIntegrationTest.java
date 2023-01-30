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

package org.axonframework.extensions.multitenancy.integration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryBus;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryUpdateEmitter;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.test.server.AxonServerSEContainer;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.axonframework.extensions.multitenancy.autoconfig.TenantConfiguration.TENANT_CORRELATION_KEY;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MultiTenancyIntegrationTest {

    public static final DockerImageName AXON_SERVER_IMAGE =
            DockerImageName.parse("axoniq/axonserver:latest");

    private ApplicationContextRunner testApplicationContext;

    @Container
    private static final AxonServerSEContainer AXON_SERVER_CONTAINER = new AxonServerSEContainer(AXON_SERVER_IMAGE);

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=true")
                .withPropertyValues("axon.axonserver.servers=" + AXON_SERVER_CONTAINER.getAxonServerAddress())
                .withUserConfiguration(DefaultContext.class);
    }

    @Test
    void willUseRegisteredTenantForCommand() {
        testApplicationContext
                .run(context -> {
                    CommandBus commandBus = context.getBean(CommandBus.class);
                    assertNotNull(commandBus);
                    assertTrue(commandBus instanceof MultiTenantCommandBus);
                    registerTenant((MultiTenantCommandBus) commandBus);
                    subscribeCommandHandler(commandBus);
                    executeCommand(commandBus);
                });
    }

    @Test
    void commandFailsWhenNoTenantSet() {
        testApplicationContext
                .run(context -> {
                    CommandBus commandBus = context.getBean(CommandBus.class);
                    assertNotNull(commandBus);
                    assertTrue(commandBus instanceof MultiTenantCommandBus);
                    executeCommandWhileTenantNotSet(commandBus);
                });
    }

    @Test
    void willUseRegisteredTenantForQuery() {
        testApplicationContext
                .run(context -> {
                    QueryUpdateEmitter emitter = context.getBean(QueryUpdateEmitter.class);
                    assertNotNull(emitter);
                    assertTrue(emitter instanceof MultiTenantQueryUpdateEmitter);
                    QueryBus queryBus = context.getBean(QueryBus.class);
                    assertNotNull(queryBus);
                    assertTrue(queryBus instanceof MultiTenantQueryBus);
                    registerTenant((MultiTenantQueryUpdateEmitter) emitter, (MultiTenantQueryBus) queryBus);
                    subscribeQueryHandler(queryBus);
                    executeQuery(queryBus);
                });
    }

    @Test
    void queryFailsWhenNoTenantSet() {
        testApplicationContext
                .run(context -> {
                    QueryBus queryBus = context.getBean(QueryBus.class);
                    assertNotNull(queryBus);
                    assertTrue(queryBus instanceof MultiTenantQueryBus);
                    executeQueryWhileTenantNotSet(queryBus);
                });
    }

    private void registerTenant(MultiTenantCommandBus commandBus) {
        commandBus.registerTenant(TenantDescriptor.tenantWithId("testTenant"));
    }

    private void registerTenant(MultiTenantQueryUpdateEmitter emitter, MultiTenantQueryBus queryBus) {
        emitter.registerTenant(TenantDescriptor.tenantWithId("testTenant"));
        queryBus.registerTenant(TenantDescriptor.tenantWithId("testTenant"));
    }

    private void subscribeCommandHandler(CommandBus commandBus) {
        commandBus.subscribe("testCommand", e -> "correct");
    }

    private void subscribeQueryHandler(QueryBus queryBus) {
        queryBus.subscribe("testQuery", String.class, e -> "correct");
    }

    private void executeCommand(CommandBus commandBus) {
        Message message = new GenericMessage("hi");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(TENANT_CORRELATION_KEY, "testTenant");
        CommandMessage command = new GenericCommandMessage(message, "testCommand").withMetaData(metadata);
        AtomicReference<String> result = new AtomicReference<>();
        commandBus.dispatch(command, (commandMessage, commandResultMessage) -> {
            result.set((String) commandResultMessage.getPayload());
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> result.get() != null);
        assertEquals("correct", result.get());
    }

    private void executeQuery(QueryBus queryBus) throws ExecutionException, InterruptedException {
        Message message = new GenericMessage("hi");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(TENANT_CORRELATION_KEY, "testTenant");
        QueryMessage query =
                new GenericQueryMessage(message, "testQuery", new InstanceResponseType(String.class))
                        .withMetaData(metadata);
        QueryResponseMessage<?> responseMessage = (QueryResponseMessage<?>) queryBus.query(query).get();
        assertEquals("correct", responseMessage.getPayload());
    }

    private void executeCommandWhileTenantNotSet(CommandBus commandBus) {
        Message message = new GenericMessage("hi");
        CommandMessage command = new GenericCommandMessage(message, "anotherCommand");
        AtomicReference<Throwable> result = new AtomicReference<>();
        commandBus.dispatch(command, (commandMessage, commandResultMessage) -> {
            result.set(commandResultMessage.exceptionResult());
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> result.get() != null);
        assertTrue(result.get() instanceof NoSuchTenantException);
    }

    private void executeQueryWhileTenantNotSet(QueryBus queryBus) throws ExecutionException, InterruptedException {
        Message message = new GenericMessage("hi");
        QueryMessage query = new GenericQueryMessage<>(message, "anotherQuery", new InstanceResponseType(String.class));
        assertThrows(NoSuchTenantException.class, () -> queryBus.query(query));
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    public static class DefaultContext {

    }
}
