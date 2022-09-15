/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.multitenancy.autoconfig;

/**
 * Enables static access to default {@code TENANT_CORRELATION_KEY} used to correlate tenant identifiers within {@link
 * org.axonframework.messaging.MetaData}.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class TenantConfiguration {

    public static final String TENANT_CORRELATION_KEY = "tenantId";
}
