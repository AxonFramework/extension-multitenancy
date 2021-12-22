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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Axon Server implementation of {@link TenantProvider}
 *
 * @author Stefan Dragisic
 */
public class AxonServerTenantProvider implements TenantProvider {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerTenantProvider.class);

    private final List<MultiTenantAwareComponent> tenantAwareComponents = new CopyOnWriteArrayList<>();

    private final List<TenantDescriptor> tenantDescriptors;
    private final String preDefinedContexts;
    private final TenantConnectPredicate tenantConnectPredicate;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private ConcurrentHashMap<TenantDescriptor, List<Registration>> registrationMap = new ConcurrentHashMap<>();

    private final String ADMIN_CTX = "_admin";

    public AxonServerTenantProvider(String preDefinedContexts,
                                    TenantConnectPredicate tenantConnectPredicate,
                                    AxonServerConnectionManager axonServerConnectionManager) {
        this.preDefinedContexts = preDefinedContexts;
        this.tenantConnectPredicate = tenantConnectPredicate;
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.tenantDescriptors = getInitialTenants();

        subscribeToUpdates();
    }

    public List<TenantDescriptor> getInitialTenants() {
        List<TenantDescriptor> initialTenants;

        if (StringUtils.nonEmptyOrNull(preDefinedContexts) && !preDefinedContexts.startsWith("$")) {
            initialTenants = Arrays.stream(preDefinedContexts.split(","))
                                   .map(TenantDescriptor::tenantWithId)
                                   .collect(Collectors.toList());
        } else {
            initialTenants = getTenantsAPI();
        }

        return initialTenants;
    }

    private void subscribeToUpdates() {
        ResultStream<ContextUpdate> contextUpdatesStream = axonServerConnectionManager
                .getConnection(ADMIN_CTX)
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
    }

    private void handleContextCreated(ContextUpdate contextUpdate) {
        try {
            TenantDescriptor newTenant = toTenantDescriptor(axonServerConnectionManager.getConnection(
                                                                                               ADMIN_CTX)
                                                                                       .adminChannel()
                                                                                       .getContextOverview(
                                                                                               contextUpdate.getContext())
                                                                                       .get());
            if (tenantConnectPredicate.test(newTenant)) {
                addTenant(newTenant);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public List<TenantDescriptor> getTenants() {
        return Collections.unmodifiableList(tenantDescriptors);
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
        return new TenantDescriptor(context.getName(),
                                    context.getMetaDataMap(),
                                    context.getReplicationGroup().getName());
    }

    protected void addTenant(TenantDescriptor tenantDescriptor) {
        tenantDescriptors.add(tenantDescriptor);
        tenantAwareComponents
                .forEach(bus -> registrationMap
                        .computeIfAbsent(tenantDescriptor, t -> new CopyOnWriteArrayList<>())
                        .add(bus.registerAndStartTenant(tenantDescriptor)));
    }

    protected void removeTenant(TenantDescriptor tenantDescriptor) {
        if (tenantDescriptors.remove(tenantDescriptor)) {
            registrationMap.remove(tenantDescriptor).forEach(Registration::cancel);
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
