package org.axonframework.extensions.multitenancy.commandbus;

import java.util.Map;
import java.util.Objects;

public class TenantDescriptor {

    protected String tenantId;

    protected String tenantName; //todo probably not needed

    protected Map<String, String> properties;

    public TenantDescriptor(String tenantId) {
        this.tenantId = tenantId;
        this.tenantName = tenantId;
    }

    public TenantDescriptor(String tenantId, String tenantName) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
    }

    public TenantDescriptor(String tenantId, String tenantName, Map<String, String> properties) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.properties = properties;
    }

    public String tenantId() {
        return tenantId;
    }

    public String tenantName() {
        return tenantName;
    }

    public Map<String, String> properties() {
        return properties;
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
