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
package org.axonframework.extensions.multitenancy.core;

import java.util.function.Predicate;

/**
 * Predicate that during runtime determines whether a newly registered {@link TenantDescriptor tenant} should be added
 * to the tenant-aware infrastructure components. Used for dynamic registration of tenant-specific components.
 *
 * @author Stefan Dragisic
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
public interface TenantConnectPredicate extends Predicate<TenantDescriptor> {

}
