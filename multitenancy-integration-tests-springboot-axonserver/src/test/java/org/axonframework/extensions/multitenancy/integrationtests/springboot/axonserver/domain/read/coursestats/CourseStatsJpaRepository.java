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

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for course statistics.
 * <p>
 * When {@code axon.multi-tenancy.jpa.tenant-repositories=true}, this repository
 * is automatically discovered and registered as a tenant component. No special
 * annotations are required - just extend {@link JpaRepository} (or any Spring Data
 * repository interface).
 * <p>
 * When injected into event/query handlers, this repository is automatically
 * scoped to the tenant from the message's metadata.
 */
public interface CourseStatsJpaRepository extends JpaRepository<CourseStatsReadModel, String> {
}
