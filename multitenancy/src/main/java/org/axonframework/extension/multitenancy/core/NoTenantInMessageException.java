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
package org.axonframework.extension.multitenancy.core;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception thrown when a tenant-scoped operation is requested but no tenant ID
 * is present in the message metadata.
 * <p>
 * This typically indicates that a message was not properly annotated with tenant
 * information, or the tenant correlation provider was not configured.
 * <p>
 * This is a non-transient exception because retrying the operation will not resolve
 * the missing tenant information - the message must be dispatched with proper tenant
 * metadata.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public class NoTenantInMessageException extends AxonNonTransientException {

    /**
     * Creates a new {@link NoTenantInMessageException} with the given message.
     *
     * @param message a description of the exception
     */
    public NoTenantInMessageException(String message) {
        super(message);
    }

    /**
     * Creates a new {@link NoTenantInMessageException} with the given message and cause.
     *
     * @param message a description of the exception
     * @param cause   the cause of the exception
     */
    public NoTenantInMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a {@link NoTenantInMessageException} referring to the given metadata key.
     *
     * @param metadataKey The metadata key that was expected to contain the tenant ID.
     * @return A {@link NoTenantInMessageException} with a message indicating no tenant was found.
     */
    public static NoTenantInMessageException forMetadataKey(String metadataKey) {
        return new NoTenantInMessageException(
                "No tenant ID found in message metadata with key '" + metadataKey + "'"
        );
    }
}
