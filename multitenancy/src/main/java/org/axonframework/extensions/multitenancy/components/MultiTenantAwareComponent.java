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

package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.Registration;

/**
 * Interface for components that can be registered with a {@link TenantProvider}.
 * <p>
 *
 * @author Stefan Dragisic
 */
public interface MultiTenantAwareComponent {

    /**
     * Registers the component with the given {@link TenantProvider} and starts without component. Use when the
     * component should be started by Axon lifecycle.
     * <p>
     *
     * @param tenantDescriptor for the component to register
     * @return registration used to stop the component
     */
    Registration registerTenant(TenantDescriptor tenantDescriptor);

    /**
     * Registers the component with the given {@link TenantProvider} and starts the component immediately. Use when
     * component should be started manually. Typical use case is to start components during runtime when new tenants are
     * created.
     * <p>
     *
     * @param tenantDescriptor for the component to register
     * @return registration used to stop the component
     */
    Registration registerAndStartTenant(TenantDescriptor tenantDescriptor);
}
