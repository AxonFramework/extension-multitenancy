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
package org.axonframework.extensions.multitenancy.integrationtests.axonserver.event;

import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.extensions.multitenancy.integrationtests.axonserver.shared.CourseId;
import org.axonframework.extensions.multitenancy.integrationtests.axonserver.shared.CourseTags;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * Course created event.
 *
 * @param courseId course ID.
 * @param name     course name.
 * @param capacity course capacity.
 */
@Event(name = "CourseCreated")
public record CourseCreated(
        @EventTag(key = CourseTags.COURSE_ID)
        CourseId courseId,
        String name,
        int capacity
) {
}
