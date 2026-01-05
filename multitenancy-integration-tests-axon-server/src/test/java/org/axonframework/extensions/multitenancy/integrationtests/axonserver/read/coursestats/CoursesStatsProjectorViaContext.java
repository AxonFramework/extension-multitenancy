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
package org.axonframework.extensions.multitenancy.integrationtests.axonserver.read.coursestats;

import org.axonframework.extensions.multitenancy.integrationtests.axonserver.event.CourseCreated;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Event handler that demonstrates accessing tenant-scoped components via
 * {@link ProcessingContext#component(Class)}.
 * <p>
 * This approach is useful when you need to access multiple tenant-scoped components
 * or when the component type isn't known at compile time.
 * <p>
 * Example:
 * <pre>{@code
 * @EventHandler
 * void handle(SomeEvent event, ProcessingContext context) {
 *     MyRepository repo = context.component(MyRepository.class);
 *     repo.save(...);
 * }
 * }</pre>
 *
 * @see CoursesStatsProjectorViaInjection for the direct injection approach
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"courseId"})
class CoursesStatsProjectorViaContext {

    private static final Logger logger = LoggerFactory.getLogger(CoursesStatsProjectorViaContext.class);

    @EventHandler
    void handle(CourseCreated event, ProcessingContext context, QueryUpdateEmitter emitter) {
        // Get tenant-scoped repository via ProcessingContext.component()
        CourseStatsRepository repository = context.component(CourseStatsRepository.class);

        var stats = new CoursesStats(
                event.courseId(),
                event.name(),
                event.capacity()
        );
        repository.save(stats);
        logger.info("[VIA-CONTEXT] Saved course: {}", event.courseId());

        emitter.emit(
                FindAllCourses.class,
                q -> true,
                new FindAllCourses.Result(List.of(stats))
        );
    }
}
