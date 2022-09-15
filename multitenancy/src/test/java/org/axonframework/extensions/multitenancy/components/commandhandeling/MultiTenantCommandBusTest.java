/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.multitenancy.components.commandhandeling;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class MultiTenantCommandBusTest {

    private MultiTenantCommandBus testSubject;
    private SimpleCommandBus fixtureSegment1;
    private SimpleCommandBus fixtureSegment2;

    @BeforeEach
    void setUp() {
        fixtureSegment1 = mock(SimpleCommandBus.class);
        fixtureSegment2 = mock(SimpleCommandBus.class);

        TenantCommandSegmentFactory tenantCommandSegmentFactory = t -> {
            if (t.tenantId().equals("fixtureTenant1")) {
                return fixtureSegment1;
            } else {
                return fixtureSegment2;
            }
        };
        TargetTenantResolver<CommandMessage<?>> targetTenantResolver = (m, tenants) -> TenantDescriptor.tenantWithId(
                "fixtureTenant2");

        testSubject = MultiTenantCommandBus.builder()
                                           .tenantSegmentFactory(tenantCommandSegmentFactory)
                                           .targetTenantResolver(targetTenantResolver)
                                           .build();
    }

    @Test
    public void dispatch() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("command");
        testSubject.dispatch(command);
        verify(fixtureSegment2).dispatch(command);
        verify(fixtureSegment1, times(0)).dispatch(any());
    }

    @Test
    public void dispatchWithCallback() {
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));
        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        CommandCallback<Object, Object> cc = (c, r) -> assertEquals(c.getPayload(), "command");
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("command");
        testSubject.dispatch(command, cc);
        verify(fixtureSegment2).dispatch(command, cc);
        verify(fixtureSegment1, times(0)).dispatch(any());
    }

    @Test
    public void unknownTenant() {
        NoSuchTenantException noSuchTenantException = assertThrows(NoSuchTenantException.class, () -> {
            CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("command");
            testSubject.dispatch(command);
        });
        assertEquals("Unknown tenant: fixtureTenant2", noSuchTenantException.getMessage());
    }

    @Test
    public void unknownTenantWithCallback() throws InterruptedException {
        CountDownLatch lock = new CountDownLatch(1);
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("command");
        testSubject.dispatch(command, (c, r) -> {
            assertTrue(r.isExceptional());
            assertEquals("Unknown tenant: fixtureTenant2", r.exceptionResult().getMessage());
            lock.countDown();
        });
        lock.await();
    }

    @Test
    public void unregister() {
        NoSuchTenantException noSuchTenantException = assertThrows(NoSuchTenantException.class, () -> {
            CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("command");
            testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant2")).cancel();
            testSubject.dispatch(command);
        });
        assertEquals("Unknown tenant: fixtureTenant2", noSuchTenantException.getMessage());
    }

    @Test
    public void registerTenantAfterCommandBusHaveBeenStarted() {
        when(fixtureSegment1.subscribe(anyString(), any())).thenReturn(() -> true);
        when(fixtureSegment2.subscribe(anyString(), any())).thenReturn(() -> true);

        MessageHandlerInterceptor<CommandMessage<?>> messageHandlerInterceptor = (m, chain) -> chain.proceed();
        testSubject.registerHandlerInterceptor(messageHandlerInterceptor);

        MessageDispatchInterceptor<CommandMessage<?>> messageDispatchInterceptor = messages -> (a, b) -> b;
        testSubject.registerDispatchInterceptor(messageDispatchInterceptor);

        testSubject.registerTenant(TenantDescriptor.tenantWithId("fixtureTenant1"));

        MessageHandler<CommandMessage<?>> commandHandler = c -> null;
        testSubject.subscribe("command", commandHandler);

        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));

        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("command");
        testSubject.dispatch(command);
        verify(fixtureSegment2).dispatch(command);
        verify(fixtureSegment1, times(0)).dispatch(any());

        verify(fixtureSegment1).subscribe("command", commandHandler);
        verify(fixtureSegment2).registerHandlerInterceptor(messageHandlerInterceptor);

        verify(fixtureSegment1).subscribe(any(), any());
        verify(fixtureSegment2).registerDispatchInterceptor(messageDispatchInterceptor);
    }

    @Test
    public void registerDispatchInterceptor() {
        when(fixtureSegment2.registerDispatchInterceptor(any())).thenReturn(() -> true);
        MessageDispatchInterceptor<CommandMessage<?>> messageDispatchInterceptor = messages -> (a, b) -> b;
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        Registration registration = testSubject.registerDispatchInterceptor(messageDispatchInterceptor);

        assertTrue(registration.cancel());
    }

    @Test
    public void registerHandlerInterceptor() {
        when(fixtureSegment2.registerHandlerInterceptor(any())).thenReturn(() -> true);
        MessageHandlerInterceptor<CommandMessage<?>> messageHandlerInterceptor = (m, chain) -> chain.proceed();
        testSubject.registerAndStartTenant(TenantDescriptor.tenantWithId("fixtureTenant2"));
        Registration registration = testSubject.registerHandlerInterceptor(messageHandlerInterceptor);

        assertTrue(registration.cancel());
    }
}