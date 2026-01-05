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
package org.axonframework.extensions.multitenancy.autoconfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.spring.config.EventProcessorSettings;
import org.axonframework.messaging.core.SubscribableEventSource;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorModule;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;

import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Spring customizations for event processors when multi-tenancy is disabled for a specific processor.
 * This is a copy of the Spring extension's SpringCustomizations to avoid package-private access issues.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
interface MultiTenantSpringCustomizations {

    static PooledStreamingEventProcessorModule.Customization pooledStreamingCustomizations(
            String name,
            EventProcessorSettings.PooledEventProcessorSettings settings
    ) {
        return new SpringPooledStreamingEventProcessingModuleCustomization(name, settings);
    }

    static SubscribingEventProcessorModule.Customization subscribingCustomizations(
            String name,
            EventProcessorSettings.SubscribingEventProcessorSettings settings) {
        return new SpringSubscribingEventProcessingModuleCustomization(name, settings);
    }

    class SpringSubscribingEventProcessingModuleCustomization implements SubscribingEventProcessorModule.Customization {

        private final EventProcessorSettings.SubscribingEventProcessorSettings settings;
        private final String name;

        SpringSubscribingEventProcessingModuleCustomization(
                String name,
                EventProcessorSettings.SubscribingEventProcessorSettings settings) {
            this.name = name;
            this.settings = settings;
        }

        @Override
        public SubscribingEventProcessorConfiguration apply(Configuration configuration,
                                                            SubscribingEventProcessorConfiguration subscribingEventProcessorConfiguration) {
            var messageSource = getComponent(configuration,
                                             SubscribableEventSource.class,
                                             settings.source(),
                                             null
            );
            require(messageSource != null, "Could not find a mandatory Source with name '" + settings.source()
                    + "' for event processor '" + name + "'.");

            return subscribingEventProcessorConfiguration
                    .eventSource(messageSource);
        }
    }

    class SpringPooledStreamingEventProcessingModuleCustomization
            implements PooledStreamingEventProcessorModule.Customization {

        private final EventProcessorSettings.PooledEventProcessorSettings settings;
        private final String name;

        SpringPooledStreamingEventProcessingModuleCustomization(
                String name,
                EventProcessorSettings.PooledEventProcessorSettings settings
        ) {
            this.settings = settings;
            this.name = name;
        }

        @Override
        public PooledStreamingEventProcessorConfiguration apply(
                Configuration configuration,
                PooledStreamingEventProcessorConfiguration eventProcessorConfiguration) {
            String executorName = "WorkPackage[" + name + "]";
            var scheduledExecutorService = Executors.newScheduledThreadPool(
                    settings.threadCount(),
                    new AxonThreadFactory(executorName)
            );

            var eventStore = getComponent(configuration,
                                          StreamableEventSource.class,
                                          settings.source(),
                                          null);
            require(eventStore != null,
                    "Could not find a mandatory Source with name '" + settings.source()
                            + "' for event processor '" + name + "'.");

            var tokenStore = getComponent(configuration,
                                          TokenStore.class,
                                          settings.tokenStore(),
                                          null);
            require(tokenStore != null,
                    "Could not find a mandatory TokenStore with name '" + settings.tokenStore()
                            + "' for event processor '" + name + "'."
            );

            return eventProcessorConfiguration
                    .workerExecutor(scheduledExecutorService)
                    .tokenClaimInterval(settings.tokenClaimIntervalInMillis())
                    .batchSize(settings.batchSize())
                    .initialSegmentCount(settings.initialSegmentCount())
                    .eventSource(eventStore)
                    .tokenStore(tokenStore);
        }
    }

    @Nullable
    static <T> T getComponent(@Nonnull Configuration configuration, @Nonnull Class<T> type,
                              @Nullable String name,
                              @Nullable Supplier<T> supplier) {
        Supplier<T> safeSupplier = (supplier != null) ? supplier : () -> null;
        return configuration.getOptionalComponent(type, name).orElseGet(safeSupplier);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AxonConfigurationException(message);
        }
    }
}
