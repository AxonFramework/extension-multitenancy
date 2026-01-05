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
package org.axonframework.extension.multitenancy.autoconfig;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link CorrelationDataProvider} that propagates the tenant identifier
 * from incoming messages to outgoing messages.
 * <p>
 * This provider ensures that the tenant context is preserved when messages trigger
 * new messages during processing (e.g., when a command handler publishes events,
 * or when an event handler sends queries).
 * <p>
 * If the tenant key is not present in the incoming message's metadata, a default value
 * of "unknownTenant" is used to ensure tenant information is always propagated.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see CorrelationDataProvider
 */
public class TenantCorrelationProvider implements CorrelationDataProvider {

    private static final String UNKNOWN_TENANT = "unknownTenant";

    private final String tenantCorrelationKey;

    /**
     * Constructs a tenant-specific {@link CorrelationDataProvider} using the given {@code tenantCorrelationKey}.
     *
     * @param tenantCorrelationKey The key used to store the tenant identifier in the
     *                             {@link Metadata message metadata}.
     */
    public TenantCorrelationProvider(String tenantCorrelationKey) {
        this.tenantCorrelationKey = tenantCorrelationKey;
    }

    @Nonnull
    @Override
    public Map<String, String> correlationDataFor(@Nonnull Message message) {
        Map<String, String> result = new HashMap<>();
        Metadata metadata = message.metadata();
        String tenantId = metadata.containsKey(tenantCorrelationKey)
                ? metadata.get(tenantCorrelationKey)
                : UNKNOWN_TENANT;
        result.put(tenantCorrelationKey, tenantId);
        return result;
    }
}
