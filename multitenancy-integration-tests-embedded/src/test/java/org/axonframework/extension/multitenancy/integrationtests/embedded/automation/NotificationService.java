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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple notification service for testing tenant component isolation.
 * Each tenant gets its own instance with isolated notification logs.
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public record Notification(String recipientId, String message) {}

    private final String tenantId;
    private final List<Notification> sentNotifications = Collections.synchronizedList(new ArrayList<>());

    public NotificationService(String tenantId) {
        this.tenantId = tenantId;
        log.info("[TENANT-COMPONENT] NotificationService created for tenant: {}", tenantId);
    }

    public void sendNotification(Notification notification) {
        log.info("[TENANT-COMPONENT] NotificationService[{}] received notification: {}",
                tenantId, notification.message());
        sentNotifications.add(notification);
    }

    public List<Notification> getSentNotifications() {
        return new ArrayList<>(sentNotifications);
    }

    public String getTenantId() {
        return tenantId;
    }
}
