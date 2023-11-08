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

package org.axonframework.extensions.multitenancy.components;

import org.junit.jupiter.api.*;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link TenantDescriptor}.
 *
 * @author Steven van Beelen
 */
class TenantDescriptorTest {

    private static final String TENANT_ID_ONE = "me";
    private static final String TENANT_ID_TWO = "you";

    @Test
    void equalsOnlyValidatesTenantId() {
        HashMap<String, String> testPropertiesOne = new HashMap<>();
        testPropertiesOne.put("key", "value");
        testPropertiesOne.put("key1", "value2");
        HashMap<String, String> testPropertiesTwo = new HashMap<>();
        testPropertiesOne.put("value", "key");
        testPropertiesOne.put("value2", "key1");

        TenantDescriptor testSubjectOne = TenantDescriptor.tenantWithId(TENANT_ID_ONE);
        TenantDescriptor testSubjectTwo = TenantDescriptor.tenantWithId(TENANT_ID_TWO);
        TenantDescriptor testSubjectThree = new TenantDescriptor(TENANT_ID_ONE, testPropertiesOne);
        TenantDescriptor testSubjectFour = new TenantDescriptor(TENANT_ID_TWO, testPropertiesTwo);
        TenantDescriptor testSubjectFive = new TenantDescriptor(TENANT_ID_ONE, testPropertiesTwo);

        // Validate test subject one, only matching on tenant id
        assertNotEquals(testSubjectOne, testSubjectTwo);
        assertEquals(testSubjectOne, testSubjectThree);
        assertNotEquals(testSubjectOne, testSubjectFour);
        assertEquals(testSubjectOne, testSubjectFive);
        // Validate test subject two, only matching on tenant id
        assertNotEquals(testSubjectTwo, testSubjectThree);
        assertEquals(testSubjectTwo, testSubjectFour);
        assertNotEquals(testSubjectTwo, testSubjectFive);
        // Validate test subject three, only matching on tenant id
        assertNotEquals(testSubjectThree, testSubjectFour);
        assertEquals(testSubjectThree, testSubjectFive);
        // Validate test subject four, only matching on tenant id
        assertNotEquals(testSubjectFour, testSubjectFive);
    }
}