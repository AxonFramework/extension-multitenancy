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
package org.axonframework.extensions.multitenancy.core;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.correlation.SimpleCorrelationDataProvider;

import java.util.Collection;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;

/**
 * A {@link TargetTenantResolver} implementation that resolves the target tenant from message metadata.
 * <p>
 * This resolver extracts the tenant identifier from the message's {@link Message#metadata() metadata}
 * using a configurable key (default: {@code "tenantId"}). If the metadata does not contain the
 * expected key, a {@link NoSuchTenantException} is thrown.
 * <p>
 * This is the standard resolver for metadata-based multi-tenant routing. Combined with a
 * {@link org.axonframework.messaging.core.correlation.CorrelationDataProvider} that propagates
 * the same metadata key, this enables automatic tenant context propagation throughout the
 * message handling chain.
 * <p>
 * Example usage:
 * <pre><code>
 *     // Using default metadata key "tenantId"
 *     TargetTenantResolver&lt;Message&gt; resolver = new MetadataBasedTenantResolver();
 *
 *     // Using custom metadata key
 *     TargetTenantResolver&lt;Message&gt; resolver = new MetadataBasedTenantResolver("customTenantKey");
 * </code></pre>
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see TargetTenantResolver
 * @see SimpleCorrelationDataProvider
 */
public class MetadataBasedTenantResolver implements TargetTenantResolver<Message> {

    /**
     * The default metadata key used to store the tenant identifier.
     */
    public static final String DEFAULT_TENANT_KEY = "tenantId";

    private final String metadataKey;

    /**
     * Constructs a {@link MetadataBasedTenantResolver} using the default metadata key {@code "tenantId"}.
     */
    public MetadataBasedTenantResolver() {
        this(DEFAULT_TENANT_KEY);
    }

    /**
     * Constructs a {@link MetadataBasedTenantResolver} using the specified metadata key.
     *
     * @param metadataKey The key to use when extracting the tenant identifier from message metadata.
     *                    Must not be {@code null} or empty.
     */
    public MetadataBasedTenantResolver(@Nonnull String metadataKey) {
        assertNonEmpty(metadataKey, "The metadata key must not be null or empty");
        this.metadataKey = metadataKey;
    }

    /**
     * Resolves the target tenant by extracting the tenant identifier from the message's metadata.
     *
     * @param message The message to resolve the tenant from.
     * @param tenants The available tenants (not used by this implementation).
     * @return The {@link TenantDescriptor} for the resolved tenant.
     * @throws NoSuchTenantException if the message metadata does not contain the expected tenant key.
     */
    @Override
    public TenantDescriptor apply(Message message, Collection<TenantDescriptor> tenants) {
        String tenantId = message.metadata().get(metadataKey);
        if (tenantId == null) {
            throw new NoSuchTenantException(
                    "No tenant identifier found in message metadata under key '" + metadataKey + "'"
            );
        }
        return TenantDescriptor.tenantWithId(tenantId);
    }

    /**
     * Returns the metadata key used by this resolver.
     *
     * @return The metadata key.
     */
    public String metadataKey() {
        return metadataKey;
    }
}
