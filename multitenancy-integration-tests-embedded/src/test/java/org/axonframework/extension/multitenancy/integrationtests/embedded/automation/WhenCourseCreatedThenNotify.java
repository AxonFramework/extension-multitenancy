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

import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.multitenancy.integrationtests.embedded.event.CourseCreated;
import org.axonframework.extension.multitenancy.integrationtests.embedded.event.CourseCreatedNotificationSent;
import org.axonframework.extension.multitenancy.integrationtests.embedded.shared.CourseId;
import org.axonframework.extension.multitenancy.integrationtests.embedded.shared.CourseTags;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.gateway.CommandDispatcher;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;

import java.util.concurrent.CompletableFuture;

/**
 * Stateful automation that sends notifications when courses are created.
 * <p>
 * This automation tests that tenant context is properly propagated through:
 * <ul>
 *     <li>Event handlers receiving tenant-scoped events</li>
 *     <li>CommandDispatcher propagating tenant context to dispatched commands</li>
 *     <li>Command handlers receiving tenant-scoped components (NotificationService)</li>
 * </ul>
 */
public class WhenCourseCreatedThenNotify {

    @EventHandler
    CompletableFuture<?> react(
            CourseCreated event,
            CommandDispatcher commandDispatcher,
            ProcessingContext context
    ) {
        var state = context.component(StateManager.class)
                .loadEntity(State.class, event.courseId(), context)
                .join();

        return sendNotificationIfNotAlreadySent(event.courseId(), event.name(), state, commandDispatcher);
    }

    private CompletableFuture<?> sendNotificationIfNotAlreadySent(
            CourseId courseId,
            String courseName,
            State state,
            CommandDispatcher commandDispatcher
    ) {
        var automationState = state != null ? state : new State();

        if (automationState.notified()) {
            return CompletableFuture.completedFuture(null);
        }

        return commandDispatcher.send(
                new SendCourseCreatedNotification(courseId, courseName),
                Object.class
        );
    }

    @CommandHandler
    void decide(
            SendCourseCreatedNotification command,
            @InjectEntity State state,
            ProcessingContext context
    ) {
        var automationState = state != null ? state : new State();

        if (automationState.notified()) {
            return;
        }

        // Send notification using tenant-scoped service from context
        var notificationService = context.component(NotificationService.class);
        var message = String.format("Course '%s' has been created", command.courseName());
        notificationService.sendNotification(
                new NotificationService.Notification("admin", message)
        );

        // Record that we've sent the notification
        var eventAppender = EventAppender.forContext(context);
        eventAppender.append(new CourseCreatedNotificationSent(
                command.courseId(),
                notificationService.getTenantId()
        ));
    }

    /**
     * State entity tracking whether a notification has been sent for a course.
     */
    @EventSourcedEntity(tagKey = CourseTags.COURSE_ID)
    public record State(CourseId courseId, boolean notified) {

        @EntityCreator
        public State() {
            this(null, false);
        }

        @EventSourcingHandler
        State evolve(CourseCreated event) {
            return new State(event.courseId(), this.notified);
        }

        @EventSourcingHandler
        State evolve(CourseCreatedNotificationSent event) {
            return new State(event.courseId(), true);
        }
    }
}
