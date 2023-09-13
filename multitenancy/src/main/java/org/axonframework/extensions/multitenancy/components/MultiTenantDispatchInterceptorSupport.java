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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorSupport;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

/**
 * Contract towards a tenant-aware component upon which {@link MessageDispatchInterceptor MessageDispatchInterceptors}
 * are supported.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface MultiTenantDispatchInterceptorSupport<M extends Message<?>, B extends MessageDispatchInterceptorSupport<M>>
        extends MessageDispatchInterceptorSupport<M> {

    /**
     * Returns a collection of {@link TenantDescriptor} to tenant-specific component.
     *
     * @return A collection of {@link TenantDescriptor} to tenant-specific component.
     */
    Map<TenantDescriptor, B> tenantSegments();

    /**
     * Returns a list of all registered {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
     *
     * @return A list of all registered {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
     */
    List<MessageDispatchInterceptor<? super M>> getDispatchInterceptors();

    /**
     * Returns a collection of all {@link MessageDispatchInterceptor} {@link Registration Registrations} per
     * {@link TenantDescriptor}.
     *
     * @return A collection of all {@link MessageDispatchInterceptor} {@link Registration Registrations} per
     * {@link TenantDescriptor}.
     */
    Map<TenantDescriptor, List<Registration>> getDispatchInterceptorsRegistration();

    @Override
    default Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super M> dispatchInterceptor
    ) {
        getDispatchInterceptors().add(dispatchInterceptor);
        Map<TenantDescriptor, List<Registration>> newRegistrations = new HashMap<>();
        tenantSegments().forEach(
                (tenant, bus) -> newRegistrations.computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                 .add(bus.registerDispatchInterceptor(dispatchInterceptor))
        );

        getDispatchInterceptorsRegistration().putAll(newRegistrations);

        return () -> newRegistrations.values().stream()
                                     .flatMap(Collection::stream)
                                     .filter(Objects::nonNull)
                                     .map(Registration::cancel)
                                     .reduce((prev, acc) -> prev && acc)
                                     .orElse(false);
    }
}
