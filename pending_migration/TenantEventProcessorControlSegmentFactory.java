/*
 * Copyright (c) 2010-2024. Axon Framework
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

import java.util.function.Function;

/**
 * Maps a tenant id to the context name for the EventProcessorControlService.
 * <p>
 * This interface is used to create a mapping between a given {@link TenantDescriptor} and a context name. After a
 * mapping is created, it will be used by EventProcessorControlService to associate event processor control with the
 * given context.
 *
 * @author Stefan Dragisic
 * @since 4.9.3
 */
public interface TenantEventProcessorControlSegmentFactory extends Function<TenantDescriptor, String> {

}