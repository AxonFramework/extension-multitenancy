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
package org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.read.coursestats;

import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.event.CourseCreated;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Event handler that projects course events to the JPA repository.
 * The repository parameter is automatically scoped to the tenant from the event's metadata.
 */
@Component
public class CourseStatsProjector {

    private static final Logger logger = LoggerFactory.getLogger(CourseStatsProjector.class);

    @EventHandler
    public void on(CourseCreated event, CourseStatsJpaRepository repository) {
        logger.info("Processing CourseCreated event for course: {}", event.courseId());
        repository.save(new CourseStatsReadModel(
                event.courseId().raw(),
                event.name(),
                event.capacity()
        ));
    }
}
