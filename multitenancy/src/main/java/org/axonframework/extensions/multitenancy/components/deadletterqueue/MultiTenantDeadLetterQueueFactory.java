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

package org.axonframework.extensions.multitenancy.components.deadletterqueue;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.deadletter.DeadLetter;

/**
 * Factory for creating {@link MultiTenantDeadLetterQueue} instances.
 * <p>
 * This factory is used to create a {@link MultiTenantDeadLetterQueue} for a specific processing group.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead letters} within this queue.
 * @author Stefan Dragisic
 * @since 4.8.0
 */
@FunctionalInterface
public interface MultiTenantDeadLetterQueueFactory<M extends EventMessage<?>> {

    /**
     * Returns a {@link MultiTenantDeadLetterQueue} for the given processing group.
     *
     * @param processingGroup The processing group for which to return a {@link MultiTenantDeadLetterQueue}
     * @return a {@link MultiTenantDeadLetterQueue} for the given processing group
     */
    MultiTenantDeadLetterQueue<M> getDeadLetterQueue(String processingGroup);
}
