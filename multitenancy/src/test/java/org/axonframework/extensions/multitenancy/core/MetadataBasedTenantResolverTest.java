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
package org.axonframework.extensions.multitenancy.core;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MetadataBasedTenantResolver}.
 *
 * @author Stefan Dragisic
 */
class MetadataBasedTenantResolverTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String CUSTOM_KEY = "customTenantKey";
    private static final MessageType COMMAND_TYPE = new MessageType("TestCommand");

    private static final TenantDescriptor TENANT_1 = TenantDescriptor.tenantWithId("tenant-1");
    private static final TenantDescriptor TENANT_2 = TenantDescriptor.tenantWithId("tenant-2");
    private static final Set<TenantDescriptor> TENANTS = Set.of(TENANT_1, TENANT_2);

    @Test
    void resolvesTenantFromDefaultMetadataKey() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver();

        CommandMessage message = new GenericCommandMessage(
                COMMAND_TYPE,
                "payload",
                Map.of("tenantId", TENANT_ID)
        );

        TenantDescriptor result = testSubject.resolveTenant(message, TENANTS);

        assertEquals(TENANT_1, result);
        assertEquals(TENANT_ID, result.tenantId());
    }

    @Test
    void resolvesTenantFromCustomMetadataKey() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver(CUSTOM_KEY);

        CommandMessage message = new GenericCommandMessage(
                COMMAND_TYPE,
                "payload",
                Map.of(CUSTOM_KEY, TENANT_ID)
        );

        TenantDescriptor result = testSubject.resolveTenant(message, TENANTS);

        assertEquals(TENANT_1, result);
    }

    @Test
    void throwsExceptionWhenMetadataKeyNotPresent() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver();

        CommandMessage message = new GenericCommandMessage(
                COMMAND_TYPE,
                "payload",
                Collections.emptyMap()
        );

        NoSuchTenantException exception = assertThrows(
                NoSuchTenantException.class,
                () -> testSubject.resolveTenant(message, TENANTS)
        );

        assertTrue(exception.getMessage().contains("tenantId"));
        assertTrue(exception.getMessage().contains("metadata"));
    }

    @Test
    void throwsExceptionWhenCustomKeyNotPresent() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver(CUSTOM_KEY);

        CommandMessage message = new GenericCommandMessage(
                COMMAND_TYPE,
                "payload",
                Map.of("wrongKey", TENANT_ID)
        );

        NoSuchTenantException exception = assertThrows(
                NoSuchTenantException.class,
                () -> testSubject.resolveTenant(message, TENANTS)
        );

        assertTrue(exception.getMessage().contains(CUSTOM_KEY));
    }

    @Test
    void defaultConstructorUsesDefaultKey() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver();

        assertEquals(MetadataBasedTenantResolver.DEFAULT_TENANT_KEY, testSubject.metadataKey());
        assertEquals("tenantId", testSubject.metadataKey());
    }

    @Test
    void metadataKeyAccessorReturnsConfiguredKey() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver(CUSTOM_KEY);

        assertEquals(CUSTOM_KEY, testSubject.metadataKey());
    }

    @Test
    void constructorRejectsNullMetadataKey() {
        assertThrows(
                AxonConfigurationException.class,
                () -> new MetadataBasedTenantResolver(null)
        );
    }

    @Test
    void constructorRejectsEmptyMetadataKey() {
        assertThrows(
                AxonConfigurationException.class,
                () -> new MetadataBasedTenantResolver("")
        );
    }

    @Test
    void resolvesFromMessageWithMultipleMetadataEntries() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver();

        CommandMessage message = new GenericCommandMessage(
                COMMAND_TYPE,
                "payload",
                Map.of(
                        "someOtherKey", "someValue",
                        "tenantId", TENANT_ID,
                        "anotherKey", "anotherValue"
                )
        );

        TenantDescriptor result = testSubject.resolveTenant(message, TENANTS);

        assertEquals(TENANT_1, result);
    }

    @Test
    void createsNewTenantDescriptorForUnknownTenant() {
        MetadataBasedTenantResolver testSubject = new MetadataBasedTenantResolver();
        String unknownTenantId = "unknown-tenant";

        CommandMessage message = new GenericCommandMessage(
                COMMAND_TYPE,
                "payload",
                Map.of("tenantId", unknownTenantId)
        );

        // The resolver creates a TenantDescriptor regardless of whether
        // it's in the known tenants set - that validation happens elsewhere
        TenantDescriptor result = testSubject.resolveTenant(message, TENANTS);

        assertEquals(unknownTenantId, result.tenantId());
    }
}
