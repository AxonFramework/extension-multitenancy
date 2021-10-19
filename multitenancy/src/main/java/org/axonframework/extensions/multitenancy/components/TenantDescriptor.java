package org.axonframework.extensions.multitenancy.components;

import java.util.Map;
import java.util.Objects;

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

    public String tenantId() {
        return tenantId;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public String replicationGroup() {
        return replicationGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantDescriptor)) return false;
        TenantDescriptor that = (TenantDescriptor) o;
        return tenantId.equals(that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }


    public static TenantDescriptor tenantWithId(String id) {
        return new TenantDescriptor(id);
    }

}
