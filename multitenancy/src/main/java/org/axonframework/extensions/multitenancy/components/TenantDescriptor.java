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

package org.axonframework.extensions.multitenancy.components;

import java.util.Map;
import java.util.Objects;

/**
 * A descriptor for a tenant, and is directly mapped to a context.
 * <p>
 *
 * @author Stefan Dragisic
 */
public class TenantDescriptor {

    protected String tenantId;

    protected Map<String, String> properties;

    protected String replicationGroup;

    public TenantDescriptor(String tenantId) {
        this.tenantId = tenantId;
    }

    public TenantDescriptor(String tenantId, Map<String, String> properties, String replicationGroup) {
        this.tenantId = tenantId;
        this.properties = properties;
        this.replicationGroup = replicationGroup;
    }

    /**
     * The tenant id. Directly mapped to the context name.
     * <p>
     *
     * @return the tenant id
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * The properties of the tenant. Directly mapped to the context properties.
     *
     * @return
     */
    public Map<String, String> properties() {
        return properties;
    }

    /**
     * The replication group of the tenant. Directly mapped to the context replication group.
     *
     * @return
     */
    public String replicationGroup() {
        return replicationGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TenantDescriptor)) {
            return false;
        }
        TenantDescriptor that = (TenantDescriptor) o;
        return tenantId.equals(that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }

    /**
     * @param id of the tenant
     * @return the descriptor statically created representation of the descriptor bound to the given tenant id.
     */
    public static TenantDescriptor tenantWithId(String id) {
        return new TenantDescriptor(id);
    }
}
