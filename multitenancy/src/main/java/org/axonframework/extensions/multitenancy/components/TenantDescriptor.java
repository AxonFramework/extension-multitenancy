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
package org.axonframework.extensions.multitenancy.components;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * A descriptor for tenants.
 *
 * @author Stefan Dragisic
 * @since 5.0.0
 */
public class TenantDescriptor {

    protected String tenantId;
    protected Map<String, String> properties;

    /**
     * Constructs a {@link TenantDescriptor} with the given {@code tenantId}.
     *
     * @param tenantId The identifier of this {@link TenantDescriptor}.
     */
    public TenantDescriptor(String tenantId) {
        this(tenantId, Collections.emptyMap());
    }

    /**
     * Constructs a {@link TenantDescriptor} with the given {@code tenantId} and {@code properties}.
     *
     * @param tenantId   The identifier of this {@link TenantDescriptor}.
     * @param properties The properties of this {@link TenantDescriptor}.
     */
    public TenantDescriptor(String tenantId, Map<String, String> properties) {
        this.tenantId = tenantId;
        this.properties = properties;
    }

    /**
     * Constructs a {@link TenantDescriptor} with the given {@code tenantId}.
     *
     * @param tenantId The identifier of this {@link TenantDescriptor}.
     * @return A {@link TenantDescriptor} with the given {@code tenantId}.
     */
    public static TenantDescriptor tenantWithId(String tenantId) {
        return new TenantDescriptor(tenantId);
    }

    /**
     * Returns the identifier of this tenant.
     *
     * @return The identifier of this tenant.
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Returns the properties of this tenant.
     *
     * @return The properties of this tenant.
     */
    public Map<String, String> properties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantDescriptor that = (TenantDescriptor) o;
        return Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }

    @Override
    public String toString() {
        return "TenantDescriptor{" +
                "tenantId='" + tenantId + '\'' +
                ", properties=" + properties +
                '}';
    }
}
