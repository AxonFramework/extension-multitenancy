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
package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link CorrelationDataProvider} that provides the tenant identifier as a correlation.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class TenantCorrelationProvider implements CorrelationDataProvider {

    private final String tenantCorrelationKey;

    /**
     * Construct a tenant-specific {@link CorrelationDataProvider} using the given {@code tenantCorrelationKey}.
     *
     * @param tenantCorrelationKey The key used to store the tenant identifier in the
     *                             {@link org.axonframework.messaging.MetaData}.
     */
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
