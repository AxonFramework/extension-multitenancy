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
package org.axonframework.extension.multitenancy.integrationtests.embedded.automation;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.extension.multitenancy.integrationtests.embedded.shared.CourseId;
import org.axonframework.extension.multitenancy.messaging.eventhandling.processing.MultiTenantPooledStreamingEventProcessorModule;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;

/**
 * Configuration for the course notification automation.
 */
public class CourseNotificationConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        // Register the automation's state entity
        var stateEntity = EventSourcedEntityModule.autodetected(
                CourseId.class,
                WhenCourseCreatedThenNotify.State.class
        );
        configurer.registerEntity(stateEntity);

        // Register the event processor for the automation
        var processor = MultiTenantPooledStreamingEventProcessorModule
                .create("CourseNotifications")
                .eventHandlingComponents(c -> c
                        .autodetected(cfg -> new WhenCourseCreatedThenNotify())
                )
                .notCustomized();

        configurer.componentRegistry(cr -> cr.registerModule(processor.build()));

        // Register the command handler for the automation
        var commandHandlingModule = CommandHandlingModule
                .named("SendCourseCreatedNotificationHandler")
                .commandHandlers()
                .annotatedCommandHandlingComponent(cfg -> new WhenCourseCreatedThenNotify())
                .build();

        configurer.registerCommandHandlingModule(commandHandlingModule);

        return configurer;
    }
}
