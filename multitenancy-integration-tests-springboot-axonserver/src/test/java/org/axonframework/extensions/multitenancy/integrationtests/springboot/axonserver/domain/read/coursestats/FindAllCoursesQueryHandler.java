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
package org.axonframework.extensions.multitenancy.integrationtests.springboot.axonserver.domain.read.coursestats;

import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Query handler for finding all courses.
 * The repository parameter is automatically scoped to the tenant from the query's metadata.
 */
@Component
public class FindAllCoursesQueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(FindAllCoursesQueryHandler.class);

    @QueryHandler
    public FindAllCourses.Result handle(FindAllCourses query, CourseStatsJpaRepository repository) {
        logger.info("Handling FindAllCourses query");
        return new FindAllCourses.Result(repository.findAll());
    }
}
