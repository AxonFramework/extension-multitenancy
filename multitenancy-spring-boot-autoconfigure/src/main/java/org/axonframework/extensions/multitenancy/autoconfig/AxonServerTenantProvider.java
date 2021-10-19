package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.Registration;
import org.axonframework.common.StringUtils;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Axon Server implementation of {@link TenantProvider}
 *
 * @author Stefan Dragisic
 */
public class AxonServerTenantProvider implements TenantProvider { //todo AS fix roles

    private final List<MultiTenantAwareComponent> tenantAwareComponents = new CopyOnWriteArrayList<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int scrapeInitialDelay = 5;
    private int scrapeInterval = 5;
    private final List<TenantDescriptor> tenantDescriptors;
    private String preDefinedContexts;
    private final TenantConnectPredicate tenantConnectPredicate;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private final AxonServerConfiguration axonServerConfiguration;
    private ConcurrentHashMap<TenantDescriptor, List<Registration>> registrationMap = new ConcurrentHashMap<>();

    {
        if (!StringUtils.nonEmptyOrNull(preDefinedContexts)) {
            scheduler.scheduleAtFixedRate(this::doUpdate,
                    scrapeInitialDelay, scrapeInterval, TimeUnit.SECONDS);
        }
    }

    public AxonServerTenantProvider(String preDefinedContexts,
                                    TenantConnectPredicate tenantConnectPredicate,
                                    AxonServerConnectionManager axonServerConnectionManager,
                                    AxonServerConfiguration axonServerConfiguration) {
        this.preDefinedContexts = preDefinedContexts;
        this.tenantConnectPredicate = tenantConnectPredicate;
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.axonServerConfiguration = axonServerConfiguration;
        this.tenantDescriptors = getInitialTenants();
    }

    public AxonServerTenantProvider(String preDefinedContexts,
                                    TenantConnectPredicate tenantConnectPredicate,
                                    AxonServerConnectionManager axonServerConnectionManager,
                                    AxonServerConfiguration axonServerConfiguration,
                                    int scrapeInitialDelay,
                                    int scrapeInterval) {
        this(preDefinedContexts, tenantConnectPredicate, axonServerConnectionManager, axonServerConfiguration);
        this.scrapeInitialDelay = scrapeInitialDelay;
        this.scrapeInterval = scrapeInterval;
    }

    private void doUpdate() {
        List<TenantDescriptor> toRemoveTenants = new ArrayList<>(tenantDescriptors);
        List<TenantDescriptor> toAddTenants = new ArrayList<>(getTenantsAPI());

        toRemoveTenants.removeAll(getTenantsAPI());
        toAddTenants.removeAll(tenantDescriptors);

        toAddTenants.stream()
                .filter(tenantConnectPredicate)
                .forEach(this::addTenant);

        toRemoveTenants
                .forEach(this::removeTenant);

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

    @Override
    public List<TenantDescriptor> getTenants() {
        return Collections.unmodifiableList(tenantDescriptors);
    }


    private List<TenantDescriptor> getTenantsAPI() {
        //todo, move to bean
        return Objects.requireNonNull(restTemplate.exchange("http://" + axonServerConfiguration.routingServers().get(0).getHostName() + ":8024/v1/public/context",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<ContextObject>>() {
                        }).getBody()).stream()
                .map(context -> new TenantDescriptor(context.getContext(), context.getMetaData(), context.getReplicationGroup()))
                .filter(tenantConnectPredicate)
                .collect(Collectors.toList());
    }

    protected void addTenant(TenantDescriptor tenantDescriptor) {
        tenantDescriptors.add(tenantDescriptor);
        tenantAwareComponents
                .forEach(bus -> registrationMap
                        .computeIfAbsent(tenantDescriptor, t -> new CopyOnWriteArrayList<>())
                        .add(bus.registerTenantAndSubscribe(tenantDescriptor)));
    }

    protected void removeTenant(TenantDescriptor tenantDescriptor) {
        tenantDescriptors.remove(tenantDescriptor);
        registrationMap.remove(tenantDescriptor).forEach(Registration::cancel);
        axonServerConnectionManager.disconnect(tenantDescriptor.tenantId());
    }

    @Override
    public Registration subscribe(MultiTenantAwareComponent bus) {
        tenantAwareComponents.add(bus);

        tenantDescriptors
                .forEach(tenantDescriptor -> registrationMap
                        .computeIfAbsent(tenantDescriptor, t -> new CopyOnWriteArrayList<>())
                        .add(bus.registerTenant(tenantDescriptor)));

        return () -> {
            scheduler.shutdown();
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
