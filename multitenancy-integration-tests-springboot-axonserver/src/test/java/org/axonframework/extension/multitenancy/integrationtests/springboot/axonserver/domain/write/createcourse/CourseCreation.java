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
package org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.write.createcourse;

import jakarta.validation.Valid;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.event.CourseCreated;
import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.shared.CourseId;
import org.axonframework.extension.multitenancy.integrationtests.springboot.axonserver.domain.shared.CourseTags;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * Entity for course creation with internal command handler.
 * Uses {@code @EventSourced} (Spring stereotype) which includes
 * {@code @Component} and {@code @Scope("prototype")}.
 */
@EventSourced(tagKey = CourseTags.COURSE_ID, idType = CourseId.class)
public class CourseCreation {

    private boolean created = false;
    private CourseId id;
    private int capacity;

    @CommandHandler
    public static void handle(@Valid CreateCourse command, EventAppender appender) {
        appender.append(
                new CourseCreated(
                        command.courseId(),
                        command.name(),
                        command.capacity()
                )
        );
    }

    @EntityCreator
    public CourseCreation() {
    }

    @EventSourcingHandler
    public void evolve(CourseCreated courseCreated) {
        this.id = courseCreated.courseId();
        this.created = true;
        this.capacity = courseCreated.capacity();
    }
}
