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
package org.axonframework.extensions.multitenancy.integrationtests.springboot.embedded.domain.shared;

import org.axonframework.extensions.multitenancy.core.TenantDescriptor;
import org.axonframework.extensions.multitenancy.spring.TenantComponent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A simple tenant-scoped service for testing {@link TenantComponent} auto-configuration.
 * <p>
 * This service records audit entries for each tenant. Each tenant gets their own instance
 * with isolated audit logs, demonstrating tenant-scoped component injection.
 * <p>
 * Note: This class does NOT have @Component annotation - it's discovered and managed
 * by the TenantComponent auto-configuration.
 * <p>
 * This class also demonstrates that TenantComponent implementations can receive Spring
 * dependencies (like {@link Clock}) via constructor injection.
 */
public class TenantAuditService implements TenantComponent<TenantAuditService> {

    /**
     * Static registry to track all audit entries across all tenant instances.
     * Used by tests to verify tenant isolation.
     */
    private static final List<AuditEntry> ALL_AUDIT_ENTRIES = new CopyOnWriteArrayList<>();

    private final Clock clock;
    private final String tenantId;
    private final List<String> localAuditLog = new ArrayList<>();

    /**
     * Constructor for the factory instance (no tenant context).
     * Spring will use this constructor when creating the factory via AutowireCapableBeanFactory,
     * injecting the Clock bean.
     */
    public TenantAuditService(Clock clock) {
        this.clock = clock;
        this.tenantId = null;
    }

    /**
     * Constructor for tenant-specific instances.
     * The Clock dependency is passed from the factory instance.
     */
    private TenantAuditService(Clock clock, String tenantId) {
        this.clock = clock;
        this.tenantId = tenantId;
    }

    @Override
    public TenantAuditService createForTenant(TenantDescriptor tenant) {
        return new TenantAuditService(this.clock, tenant.tenantId());
    }

    /**
     * Records an audit entry for this tenant.
     * Uses the injected Clock to timestamp the entry, proving Spring DI works.
     */
    public void recordAudit(String action) {
        if (tenantId == null) {
            throw new IllegalStateException("Cannot record audit on factory instance - no tenant context");
        }
        if (clock == null) {
            throw new IllegalStateException("Clock was not injected - Spring DI failed!");
        }
        Instant timestamp = clock.instant();
        String entry = tenantId + ":" + action + "@" + timestamp;
        localAuditLog.add(entry);
        ALL_AUDIT_ENTRIES.add(new AuditEntry(tenantId, action, timestamp));
    }

    /**
     * Returns the tenant ID this service instance is scoped to.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Returns the local audit log for this tenant instance.
     */
    public List<String> getLocalAuditLog() {
        return Collections.unmodifiableList(localAuditLog);
    }

    /**
     * Returns all audit entries across all tenants (for test verification).
     */
    public static List<AuditEntry> getAllAuditEntries() {
        return Collections.unmodifiableList(ALL_AUDIT_ENTRIES);
    }

    /**
     * Clears all audit entries (call in test setup).
     */
    public static void clearAllAuditEntries() {
        ALL_AUDIT_ENTRIES.clear();
    }

    /**
     * Returns audit entries for a specific tenant.
     */
    public static List<AuditEntry> getAuditEntriesForTenant(String tenantId) {
        return ALL_AUDIT_ENTRIES.stream()
                .filter(e -> e.tenantId().equals(tenantId))
                .toList();
    }

    /**
     * Audit entry record with timestamp to prove Clock injection works.
     */
    public record AuditEntry(String tenantId, String action, Instant timestamp) {
    }
}
