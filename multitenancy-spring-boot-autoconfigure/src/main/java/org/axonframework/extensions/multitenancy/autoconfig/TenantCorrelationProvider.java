package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link CorrelationDataProvider} that provides the tenant identifier as a correlation.
 * <p>
 *
 * @author Stefan Dragisic
 */
public class TenantCorrelationProvider implements CorrelationDataProvider {

    private final String tenantCorrelationKey;

    public TenantCorrelationProvider(String tenantCorrelationKey) {
        this.tenantCorrelationKey = tenantCorrelationKey;
    }

    @Override
    public Map<String, ?> correlationDataFor(Message<?> message) {
        Map<String, Object> result = new HashMap<>();
        result.put(tenantCorrelationKey, message.getMetaData().getOrDefault(tenantCorrelationKey, "unknownTenant"));
        return result;
    }
}
