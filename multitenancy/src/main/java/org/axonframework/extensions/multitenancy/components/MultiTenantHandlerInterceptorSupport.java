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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorSupport;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Stefan Dragisic
 */
public interface MultiTenantHandlerInterceptorSupport<M extends Message<?>, B extends MessageHandlerInterceptorSupport<M>>
        extends MessageHandlerInterceptorSupport<M> {

    Map<TenantDescriptor, B> tenantSegments();

    List<MessageHandlerInterceptor<? super M>> getHandlerInterceptors();

    Map<TenantDescriptor, List<Registration>> getHandlerInterceptorsRegistration();

    @Override
    default Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super M> handlerInterceptor) {
        getHandlerInterceptors().add(handlerInterceptor);
        Map<TenantDescriptor, List<Registration>> newRegistrations = new HashMap<>();
        tenantSegments().forEach((tenant, bus) ->
                                         newRegistrations
                                                 .computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>())
                                                 .add(bus.registerHandlerInterceptor(handlerInterceptor)));

        getHandlerInterceptorsRegistration().putAll(newRegistrations);

        return () -> newRegistrations.values()
                                     .stream()
                                     .flatMap(Collection::stream)
                                     .map(Registration::cancel)
                                     .reduce((prev, acc) -> prev && acc)
                                     .orElse(false);
    }
}
