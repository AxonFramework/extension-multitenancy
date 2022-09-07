package org.axonframework.extensions.multitenancy.autoconfig;

/**
 * Enales static access to default {@code TENANT_CORRELATION_KEY} used to correlate tenant identifiers within {@link
 * org.axonframework.messaging.MetaData}
 * <p>
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class TenantConfiguration {
    public static final String TENANT_CORRELATION_KEY = "tenantId";
}
