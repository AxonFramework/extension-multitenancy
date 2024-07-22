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
package org.axonframework.extensions.multitenancy.components;

import org.axonframework.common.Registration;

/**
 * Interface for components that can be registered with a {@link TenantProvider}.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 *
 */
public interface MultiTenantAwareComponent {

    /**
     * Registers the given {@code tenantDescriptor} as a known tenant with this multi-tenant aware component.
     *
     * @param tenantDescriptor The {@link TenantDescriptor} to register with this component.
     * @return A {@link Registration} used to deregister the given {@code tenantDescriptor}.
     */
    Registration registerTenant(TenantDescriptor tenantDescriptor);

    /**
     * Registers the given {@code tenantDescriptor} as a known tenant with this multi-tenant aware component. If
     * applicable, this task will construct a tenant segment and start it.
     *
     * @param tenantDescriptor The {@link TenantDescriptor} to register with this component.
     * @return A {@link Registration} used to deregister the given {@code tenantDescriptor}.
     */
    Registration registerAndStartTenant(TenantDescriptor tenantDescriptor);
}