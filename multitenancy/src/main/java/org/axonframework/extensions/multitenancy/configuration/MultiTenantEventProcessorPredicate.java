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
package org.axonframework.extensions.multitenancy.configuration;

import java.util.function.Predicate;

/**
 * Represents a predicate to determine if an event processor should be multi-tenant.
 *
 * This interface extends {@link Predicate<String>} and is used to test whether a given event processor
 * should be considered as multi-tenant. The input to the predicate is the name of the event processor.
 *
 * @author Stefan Dragisic
 * @since 4.9.3
 */
public interface MultiTenantEventProcessorPredicate extends Predicate<String> { }
