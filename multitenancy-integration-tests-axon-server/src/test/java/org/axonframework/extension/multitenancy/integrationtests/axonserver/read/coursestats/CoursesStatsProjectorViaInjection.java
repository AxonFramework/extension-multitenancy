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
package org.axonframework.extension.multitenancy.integrationtests.axonserver.read.coursestats;

import org.axonframework.extension.multitenancy.integrationtests.axonserver.event.CourseCreated;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.PropertySequencingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event handler that demonstrates accessing tenant-scoped components via
 * direct parameter injection.
 * <p>
 * This approach is the most concise when you know the component type at compile time
 * and only need a single tenant-scoped component.
 * <p>
 * Example:
 * <pre>{@code
 * @EventHandler
 * void handle(SomeEvent event, MyRepository repository) {
 *     repository.save(...);
 * }
 * }</pre>
 *
 * @see CoursesStatsProjectorViaContext for the ProcessingContext approach
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"courseId"})
class CoursesStatsProjectorViaInjection {

    private static final Logger logger = LoggerFactory.getLogger(CoursesStatsProjectorViaInjection.class);

    @EventHandler
    void handle(CourseCreated event, CourseStatsRepository repository) {
        // Repository is injected directly as a parameter - tenant is resolved automatically
        var stats = new CoursesStats(
                event.courseId(),
                event.name(),
                event.capacity()
        );
        repository.save(stats);
        logger.info("[VIA-INJECTION] Saved course: {}", event.courseId());
    }
}
