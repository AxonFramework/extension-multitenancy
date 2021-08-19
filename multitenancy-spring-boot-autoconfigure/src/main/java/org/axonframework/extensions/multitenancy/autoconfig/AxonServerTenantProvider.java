package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.common.StringUtils;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantBus;
import org.axonframework.extensions.multitenancy.commandbus.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.commandbus.TenantDescriptor;
import org.axonframework.extensions.multitenancy.commandbus.TenantProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Stefan Dragisic
 */
public class AxonServerTenantProvider implements TenantProvider {

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final List<MultiTenantBus> buses = new CopyOnWriteArrayList<>();

    private List<TenantDescriptor> tenantDescriptors = new CopyOnWriteArrayList<>();

    private String preDefinedContexts;

    private TenantConnectPredicate tenantConnectPredicate;

    private final RestTemplate restTemplate = new RestTemplate();

    public AxonServerTenantProvider(String preDefinedContexts, TenantConnectPredicate tenantConnectPredicate) {
        this.preDefinedContexts = preDefinedContexts;
        this.tenantConnectPredicate = tenantConnectPredicate;
    }

    public AxonServerTenantProvider() {
    }

    {
        if (!StringUtils.nonEmptyOrNull(preDefinedContexts)) {
            scheduler.scheduleAtFixedRate(() -> Objects.requireNonNull(updates()).stream()
                            .filter(Objects::nonNull)
                            .forEach(tenantDescriptor -> buses.forEach(bus -> bus.registerAndSubscribeTenant(tenantDescriptor))),
                    5, 5, TimeUnit.SECONDS); //todo parametarize
        }
    }

    private List<TenantDescriptor> updates() {
//
//        List<TenantDescriptor> latestTenants = getTenantsAPI();
//        latestTenants.removeAll(tenantDescriptors); //new to add
//
//        latestTenants.retainAll(null); //todo unregister

        return get().stream()
                .filter(tenantConnectPredicate)
                .peek(tenantDescriptor -> {
                    buses.forEach(it -> it.registerAndSubscribeTenant(tenantDescriptor));
                })
                .collect(Collectors.toList());
    }

    //gets called initially
    @Override
    public List<TenantDescriptor> get() {
        System.out.println("Getting tenants list...");
//        if (!tenantDescriptors.isEmpty()) {
//            return Collections.unmodifiableList(tenantDescriptors);
//        }

        if (StringUtils.nonEmptyOrNull(preDefinedContexts)) {
            tenantDescriptors = Arrays.stream(preDefinedContexts.split(","))
                    .map(TenantDescriptor::tenantWithId)
                    .collect(Collectors.toList());
        } else {
            tenantDescriptors = getTenantsAPI();
        }

        return Collections.unmodifiableList(tenantDescriptors);
    }

    private List<TenantDescriptor> getTenantsAPI() {
        //todo url from properties
        //todo add replication group and node tags to meta data
        return Objects.requireNonNull(restTemplate.exchange("http://localhost:8024/v1/public/context", HttpMethod.GET, null, new ParameterizedTypeReference<List<ContextObject>>() {
        }).getBody()).stream().map(context -> new TenantDescriptor(context.getContext(), context.getMetaData())).filter(tenantConnectPredicate).collect(Collectors.toList());
    }

    @Override
    public void subscribeTenantUpdates(MultiTenantBus bus) {
        if (!StringUtils.nonEmptyOrNull(preDefinedContexts)) {
            buses.add(bus);
        }
    }


}
