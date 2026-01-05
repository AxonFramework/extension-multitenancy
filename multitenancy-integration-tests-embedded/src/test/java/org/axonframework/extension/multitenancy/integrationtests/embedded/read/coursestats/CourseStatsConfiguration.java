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
package org.axonframework.extension.multitenancy.integrationtests.embedded.read.coursestats;

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.extension.multitenancy.messaging.eventhandling.processing.MultiTenantPooledStreamingEventProcessorModule;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;

/**
 * Configuration for course statistics projection and query handlers.
 * <p>
 * Demonstrates two approaches for accessing tenant-scoped components using
 * separate event processors:
 * <ol>
 *   <li>{@link CoursesStatsProjectorViaInjection} - Direct parameter injection</li>
 *   <li>{@link CoursesStatsProjectorViaContext} - Via {@code ProcessingContext.component()}</li>
 * </ol>
 * <p>
 * Note: Tenant-scoped repository is registered separately via
 * {@code MultiTenancyConfigurer.tenantComponent()} for global access.
 */
public class CourseStatsConfiguration {

    public static final String PROCESSOR_VIA_INJECTION = "Projection_CourseStats_ViaInjection";
    public static final String PROCESSOR_VIA_CONTEXT = "Projection_CourseStats_ViaContext";

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        // Processor 1: Demonstrates direct parameter injection approach
        var injectionProcessor = MultiTenantPooledStreamingEventProcessorModule
                .create(PROCESSOR_VIA_INJECTION)
                .eventHandlingComponents(c -> c
                        .autodetected(cfg -> new CoursesStatsProjectorViaInjection())
                )
                .notCustomized();

        // Processor 2: Demonstrates ProcessingContext.component() approach
        var contextProcessor = MultiTenantPooledStreamingEventProcessorModule
                .create(PROCESSOR_VIA_CONTEXT)
                .eventHandlingComponents(c -> c
                        .autodetected(cfg -> new CoursesStatsProjectorViaContext())
                )
                .notCustomized();

        // Query handler uses direct parameter injection for tenant-scoped repository
        QueryHandlingModule queryModule = QueryHandlingModule.named("Stats-Handler")
                .queryHandlers()
                .annotatedQueryHandlingComponent(cfg -> new FindAllCoursesQueryHandler())
                .build();

        return configurer
                .componentRegistry(cr -> cr.registerModule(injectionProcessor.build()))
                .componentRegistry(cr -> cr.registerModule(contextProcessor.build()))
                .registerQueryHandlingModule(queryModule);
    }

    private CourseStatsConfiguration() {
        // Prevent instantiation
    }
}
