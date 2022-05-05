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

import java.util.List;

/**
 * Registers new and manages currently registered tenants and {@link MultiTenantAwareComponent} components. If
 * configured monitors tenants changes and updates the {@link MultiTenantAwareComponent} components accordingly.
 * <p>
 *
 * @author Stefan Dragisic
 */
public interface TenantProvider {

    /**
     * @return the list of currently registered tenants.
     */
    List<TenantDescriptor> getTenants();

    /**
     * @param component to be subscribed {@link MultiTenantAwareComponent} for tenant changes.
     * @return the registration for the given component.
     */
    Registration subscribe(MultiTenantAwareComponent component);
}
