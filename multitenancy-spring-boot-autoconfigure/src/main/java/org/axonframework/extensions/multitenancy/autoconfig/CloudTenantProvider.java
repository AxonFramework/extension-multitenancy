/*
 * Copyright (c) 2010-2022. Axon Framework
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

import io.axoniq.axonserver.grpc.admin.ContextOverview;
import io.axoniq.axonserver.grpc.admin.ContextUpdate;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.Registration;
import org.axonframework.common.StringUtils;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

/**
 * Axon Cloud implementation of {@link TenantProvider}
 *
 * @author Stefan Dragisic
 */
public class CloudTenantProvider implements TenantProvider {

    private static final Logger logger = LoggerFactory.getLogger(CloudTenantProvider.class);

    private final List<MultiTenantAwareComponent> tenantAwareComponents = new CopyOnWriteArrayList<>();

    private final Set<TenantDescriptor> tenantDescriptors = new HashSet<>();
    private final String preDefinedContexts;
    private final TenantConnectPredicate tenantConnectPredicate;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private ConcurrentHashMap<TenantDescriptor, List<Registration>> registrationMap = new ConcurrentHashMap<>();

    public CloudTenantProvider(String preDefinedContexts,
                               TenantConnectPredicate tenantConnectPredicate,
                               //restTemplate
                               AxonServerConnectionManager axonServerConnectionManager) {
        this.preDefinedContexts = preDefinedContexts;
        this.tenantConnectPredicate = tenantConnectPredicate;
        this.axonServerConnectionManager = axonServerConnectionManager;
    }

    @PostConstruct
    public void start() {
        tenantDescriptors.addAll(getInitialTenants());
        if (preDefinedContexts == null || preDefinedContexts.isEmpty()) {
            subscribeToUpdates();
        }
    }

    public List<TenantDescriptor> getInitialTenants() {
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
        //how to subscribe from java to SSE, server sent events (/api/space/{spaceId}/context/updates)
 //      try {
            //call -> /api/space/{spaceId}/context/updates
            //get space id from context name, split by @ second part is spaceId
//            ResultStream<ContextUpdate> contextUpdatesStream = axonServerConnectionManager
//                    .getConnection(ADMIN_CTX)
//                    .adminChannel()
//                    .subscribeToContextUpdates();


//            contextUpdatesStream.onAvailable(() -> {

        //     THIS PART IS THE SAME no need to change
//                try {
//                    ContextUpdate contextUpdate = contextUpdatesStream.nextIfAvailable();
//                    if (contextUpdate != null) {
//                        switch (contextUpdate.getType()) {
//                            case CREATED:
//                                handleContextCreated(contextUpdate);
//                                break;
//                            case DELETED:
//                                removeTenant(TenantDescriptor.tenantWithId(contextUpdate.getContext()));
//                        }
//                    }
//                } catch (Exception e) {
//                    logger.error(e.getMessage(), e);
//                }
//            });
//        } catch (Exception e) {
//            logger.error("Error while subscribing to context updates", e);
//        }
    }

    private void handleContextCreated(ContextUpdate contextUpdate) {
        try {
            ContextOverview contextOverview = null; //call /api/space/{spaceId}/context/{id}
            TenantDescriptor newTenant = toTenantDescriptor(contextOverview);
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
        //call rest endpoint /api/space/{spaceId}/context and return list of TenantDescriptors

        //this you will not need
//        return axonServerConnectionManager.getConnection(ADMIN_CTX)
//                                          .adminChannel()
//                                          .getAllContexts()
//                                          .join()
//                                          .stream()

        //this you will need
//                                          .map(this::toTenantDescriptor)
//                                          .filter(tenantConnectPredicate)
//                                          .collect(Collectors.toList());
        return null;
    }

    private TenantDescriptor toTenantDescriptor(ContextOverview context) {

        //put to meta data ->
//        "contextId": "string",
//                "contextName": "string",
//                "spaceId": "string",
//                "type": "string",
//                "free": true,
//                "contextStatus": "string",
//                "region": "string",
//                "creationTime": "2022-07-15T10:16:37.710Z",
//                "scheduledCleanupTime": "2022-07-15T10:16:37.710Z",
//                "clusterId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
//                "contextClusterType": "string"

        return new TenantDescriptor(context.getName(),
                                    context.getMetaDataMap(), // <---------
                                    "");
    }

    protected void addTenant(TenantDescriptor tenantDescriptor) {
        tenantDescriptors.add(tenantDescriptor);
        tenantAwareComponents
                .forEach(bus -> registrationMap
                        .computeIfAbsent(tenantDescriptor, t -> new CopyOnWriteArrayList<>())
                        .add(bus.registerAndStartTenant(tenantDescriptor)));
    }

    protected void removeTenant(TenantDescriptor tenantDescriptor) {
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
}