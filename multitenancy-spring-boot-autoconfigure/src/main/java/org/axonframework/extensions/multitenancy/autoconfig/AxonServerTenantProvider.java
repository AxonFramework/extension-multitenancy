/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.multitenancy.autoconfig;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.grpc.admin.ContextOverview;
import io.axoniq.axonserver.grpc.admin.ContextUpdate;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.Registration;
import org.axonframework.common.StringUtils;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Axon Server implementation of the {@link TenantProvider}. Uses Axon Server's multi-context support to construct
 * tenant-specific segments of all {@link MultiTenantAwareComponent MultiTenantAwareComponents}.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class AxonServerTenantProvider implements TenantProvider, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerTenantProvider.class);

    private final List<MultiTenantAwareComponent> tenantAwareComponents = new CopyOnWriteArrayList<>();

    private final Set<TenantDescriptor> tenantDescriptors = new HashSet<>();
    private final String preDefinedContexts;
    private final TenantConnectPredicate tenantConnectPredicate;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private final String ADMIN_CTX = "_admin";
    private ConcurrentHashMap<TenantDescriptor, List<Registration>> registrationMap = new ConcurrentHashMap<>();

    /**
     * Construct a {@link AxonServerTenantProvider}.
     *
     * @param preDefinedContexts          A comma-separated list of all the base contexts used for the tenants.
     * @param tenantConnectPredicate      A {@link java.util.function.Predicate} used to filter out newly registered
     *                                    contexts as tenants.
     * @param axonServerConnectionManager The {@link AxonServerConnectionManager} used to retrieve all context-specific
     *                                    changes through to make adjustments in the tenant-specific segments.
     */
    public AxonServerTenantProvider(String preDefinedContexts,
                                    TenantConnectPredicate tenantConnectPredicate,
                                    AxonServerConnectionManager axonServerConnectionManager) {
        this.preDefinedContexts = preDefinedContexts;
        this.tenantConnectPredicate = tenantConnectPredicate;
        this.axonServerConnectionManager = axonServerConnectionManager;
    }

    /**
     * Start this {@link TenantProvider}, by added all tenants and subscribing to the
     * {@link AxonServerConnectionManager} for context updates.
     */
    public void start() {
        tenantDescriptors.addAll(getInitialTenants());
        tenantDescriptors.forEach(this::addTenant);
        if (preDefinedContexts == null || preDefinedContexts.isEmpty()) {
            subscribeToUpdates();
        }
    }

    private List<TenantDescriptor> getInitialTenants() {
        List<TenantDescriptor> initialTenants = Collections.emptyList();
        try {
            if (StringUtils.nonEmptyOrNull(preDefinedContexts)) {
                initialTenants = Arrays.stream(preDefinedContexts.split(","))
                                       .map(String::trim)
                                       .map(TenantDescriptor::tenantWithId)
                                       .collect(Collectors.toList());
            } else {
                initialTenants = getTenantsAPI();
            }
        } catch (Exception e) {
            logger.error("Error while getting initial tenants", e);
        }
        return initialTenants;
    }

    private void subscribeToUpdates() {
        try {
            ResultStream<ContextUpdate> contextUpdatesStream = axonServerConnectionManager.getConnection(ADMIN_CTX)
                                                                                          .adminChannel()
                                                                                          .subscribeToContextUpdates();

            contextUpdatesStream.onAvailable(() -> {
                try {
                    ContextUpdate contextUpdate = contextUpdatesStream.nextIfAvailable();
                    if (contextUpdate != null) {
                        switch (contextUpdate.getType()) {
                            case CREATED:
                                handleContextCreated(contextUpdate);
                                break;
                            case DELETED:
                                removeTenant(TenantDescriptor.tenantWithId(contextUpdate.getContext()));
                        }
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            logger.error("Error while subscribing to context updates", e);
        }
    }

    private void handleContextCreated(ContextUpdate contextUpdate) {
        try {
            TenantDescriptor newTenant =
                    toTenantDescriptor(axonServerConnectionManager.getConnection(ADMIN_CTX)
                                                                  .adminChannel()
                                                                  .getContextOverview(contextUpdate.getContext())
                                                                  .get());
            if (tenantConnectPredicate.test(newTenant) && !tenantDescriptors.contains(newTenant)) {
                addTenant(newTenant);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public List<TenantDescriptor> getTenants() {
        return new ArrayList<>(tenantDescriptors);
    }

    private List<TenantDescriptor> getTenantsAPI() {
        return axonServerConnectionManager.getConnection(ADMIN_CTX)
                                          .adminChannel()
                                          .getAllContexts()
                                          .join()
                                          .stream()
                                          .map(this::toTenantDescriptor)
                                          .filter(tenantConnectPredicate)
                                          .collect(Collectors.toList());
    }

    private TenantDescriptor toTenantDescriptor(ContextOverview context) {
        Map<String, String> metaDataMap = new HashMap<>(context.getMetaDataMap());
        metaDataMap.putIfAbsent("replicationGroup", context.getReplicationGroup().getName());

        return new TenantDescriptor(context.getName(), metaDataMap);
    }

    /**
     * Adds a new tenant to the system.
     *
     * This method adds the provided {@link TenantDescriptor} to the set of known tenants.
     * Once added all {@link MultiTenantAwareComponent MultiTenantAwareComponents} are registered and started for the
     * new tenant.
     *
     * @param tenantDescriptor the {@link TenantDescriptor} representing the tenant to be added.
     */
    public void addTenant(TenantDescriptor tenantDescriptor) {
        tenantDescriptors.add(tenantDescriptor);
        tenantAwareComponents
                .forEach(bus -> registrationMap
                        .computeIfAbsent(tenantDescriptor, t -> new CopyOnWriteArrayList<>())
                        .add(bus.registerAndStartTenant(tenantDescriptor)));
    }

    /**
     * Removes a tenant from the system.
     *
     * This method checks if the provided {@link TenantDescriptor} is in the set of known tenants.
     * If it is, the method removes the tenant and cancels all its registrations.
     * {@link MultiTenantAwareComponent MultiTenantAwareComponents} are then unregistered in reverse order of their
     * registration.
     * It then disconnects the tenant from the Axon Server.
     *
     * @param tenantDescriptor the {@link TenantDescriptor} representing the tenant to be removed.
     */
    public void removeTenant(TenantDescriptor tenantDescriptor) {
        if (tenantDescriptors.contains(tenantDescriptor) && tenantDescriptors.remove(tenantDescriptor)) {
            List<Registration> registrations = registrationMap.remove(tenantDescriptor);
            if (registrations != null && !registrations.isEmpty()) {
                registrations.forEach(Registration::cancel);
            }
            axonServerConnectionManager.disconnect(tenantDescriptor.tenantId());
        }
    }

    @Override
    public Registration subscribe(MultiTenantAwareComponent bus) {
        tenantAwareComponents.add(bus);

        tenantDescriptors
                .forEach(tenantDescriptor -> registrationMap
                        .computeIfAbsent(tenantDescriptor, t -> new CopyOnWriteArrayList<>())
                        .add(bus.registerTenant(tenantDescriptor)));

        return () -> {
            registrationMap.forEach((tenant, registrationList) -> {
                registrationList.forEach(Registration::cancel);
                tenantAwareComponents.removeIf(t -> true);
                axonServerConnectionManager.disconnect(tenant.tenantId());
            });
            registrationMap = new ConcurrentHashMap<>();
            return true;
        };
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INSTRUCTION_COMPONENTS + 10, this::start);
        lifecycle.onShutdown(Phase.INSTRUCTION_COMPONENTS + 10, this::shutdown);
    }

    /**
     * Shuts down the AxonServerTenantProvider by deregistering all subscribed components.
     * This method is designed to be invoked by a lifecycle handler (e.g., a shutdown hook)
     * to ensure proper cleanup when the application is shutting down.
     * <p>
     * The shutdown process involves the following steps:
     * <ol>
     *     <li>Iterates through all registered components for each tenant.</li>
     *     <li>Reverses the order of registrations for each tenant to ensure
     *         last-registered components are deregistered first.</li>
     *     <li>Invokes the cancel method on each registration, effectively
     *         deregistering the component from the tenant.</li>
     * </ol>
     * <p>
     * This method ensures that all resources associated with tenant management are properly
     * released and that components are given the opportunity to perform any necessary cleanup
     * in the reverse order of their registration.
     */
    public void shutdown() {
        registrationMap.values().forEach(registrationList -> {
            ArrayList<Registration> reversed = new ArrayList<>(registrationList);
            Collections.reverse(reversed);
            reversed.forEach(Registration::cancel);
        });
    }
}
