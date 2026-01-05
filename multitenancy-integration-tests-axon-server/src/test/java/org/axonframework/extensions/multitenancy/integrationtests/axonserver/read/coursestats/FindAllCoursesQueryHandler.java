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

import org.axonframework.messaging.queryhandling.annotation.QueryHandler;

/**
 * Query handler for finding all courses.
 * <p>
 * Uses parameter injection to receive the tenant-scoped repository.
 * The repository is resolved based on the query's tenant context.
 */
public class FindAllCoursesQueryHandler {

    @QueryHandler
    FindAllCourses.Result handle(FindAllCourses query, CourseStatsRepository repository) {
        return new FindAllCourses.Result(repository.findAll());
    }
}
