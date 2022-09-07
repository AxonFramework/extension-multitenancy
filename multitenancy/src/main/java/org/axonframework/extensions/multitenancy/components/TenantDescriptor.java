package org.axonframework.extensions.multitenancy.components;

import java.util.Map;
import java.util.Objects;

/**
 * A descriptor for a tenant, and is directly mapped to a context.
 * <p>
 *
 * @author Stefan Dragisic
 * @since 4.6.0
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
