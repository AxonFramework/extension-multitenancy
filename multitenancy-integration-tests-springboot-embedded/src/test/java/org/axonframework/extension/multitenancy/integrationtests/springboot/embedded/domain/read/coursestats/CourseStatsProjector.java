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
package org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.read.coursestats;

import org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.event.CourseCreated;
import org.axonframework.extension.multitenancy.integrationtests.springboot.embedded.domain.shared.TenantAuditService;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Event handler that projects course events to the JPA repository.
 * The repository parameter is automatically scoped to the tenant from the event's metadata.
 * The audit service is also tenant-scoped via {@link org.axonframework.extension.multitenancy.spring.TenantComponent}.
 */
@Component
public class CourseStatsProjector {

    private static final Logger logger = LoggerFactory.getLogger(CourseStatsProjector.class);

    @EventHandler
    public void on(CourseCreated event, CourseStatsJpaRepository repository, TenantAuditService auditService) {
        logger.info("Processing CourseCreated event for course: {} in tenant: {}",
                event.courseId(), auditService.getTenantId());

        // Record audit entry using tenant-scoped service
        auditService.recordAudit("course_created:" + event.courseId().raw());

        repository.save(new CourseStatsReadModel(
                event.courseId().raw(),
                event.name(),
                event.capacity()
        ));
    }
}
