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

package org.axonframework.extensions.multitenancy.components.eventstore;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Function;

/**
 * Factory for creating {@link EventStore} instances for a given {@link TenantDescriptor}.
 * <p>
 *
 * @author Stefan Dragisic
 */
public interface TenantEventSegmentFactory extends Function<TenantDescriptor, EventStore> {

}
