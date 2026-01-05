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

import org.axonframework.common.Registration;

import java.util.List;

/**
 * Contract towards a component that provisions the registered set of {@link TenantDescriptor tenants} and
 * {@link MultiTenantAwareComponent MultiTenantAwareComponents}.
 * <p>
 * Depending on the implementation the provider can monitor tenant changes and update the
 * {@code MultiTenantAwareComponents} accordingly.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public interface TenantProvider {

    /**
     * Subscribes the given {@code component} with this provider.
     *
     * @param component to be subscribed {@link MultiTenantAwareComponent} for tenant changes.
     * @return the registration for the given component.
     */
    Registration subscribe(MultiTenantAwareComponent component);

    /**
     * Get the list of registered {@link TenantDescriptor tenants} with this provided.
     *
     * @return The list of registered {@link TenantDescriptor tenants}.
     */
    List<TenantDescriptor> getTenants();
}
