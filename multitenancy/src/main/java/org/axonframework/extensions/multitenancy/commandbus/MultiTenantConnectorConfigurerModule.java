package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.LoggingDuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.ModuleConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Steven van Beelen
 * @author Stefan Dragisic
 */
public class MultiTenantConnectorConfigurerModule implements ConfigurerModule, ModuleConfiguration {

    //todo
    //set tenantProvider in MultitenancyAxonServerAutoConfiguration
    //set tenantFilter from user bean?
    //set targetTenantResolver from user bean?

    private final Predicate<? super TenantDescriptor> tenantFilter = tenantDescriptor -> true; //todo change Predicate<? super TenantDescriptor>  to interface like TenantCommandSegmentFactory

    private Supplier<List<TenantDescriptor>> tenantsProvider = () -> Collections.singletonList(TenantDescriptor.tenantWithId("default")); //invoked first
    private Function<Configuration, TenantCommandSegmentFactory> tenantCommandSegmentFactory =
            config -> tenantDescriptor -> SimpleCommandBus.builder()
                    .duplicateCommandHandlerResolver(
                            config.getComponent(DuplicateCommandHandlerResolver.class,
                                    LoggingDuplicateCommandHandlerResolver::instance))
                    .messageMonitor(config.messageMonitor(CommandBus.class, "commandBus"))
                    .transactionManager(config.getComponent(TransactionManager.class, NoTransactionManager::instance))
                    .build();

    private Function<Configuration, TenantEventSegmentFactory> tenantEventSegmentFactory =
            config -> tenantDescriptor -> SimpleEventBus.builder()
                    .messageMonitor(config.messageMonitor(EventBus.class, "eventBus"))
                    .build();

    private Function<Configuration, TenantQuerySegmentFactory> tenantQuerySegmentFactory =
            config -> tenantDescriptor -> SimpleQueryBus.builder()
                    .queryUpdateEmitter(SimpleQueryUpdateEmitter.builder().build())
                    .messageMonitor(config.messageMonitor(QueryBus.class, "queryBus"))
                    .transactionManager(config.getComponent(TransactionManager.class, NoTransactionManager::instance))
                    //.errorHandler(e->e) todo ?
                    .build();

    private Function<Configuration, TargetTenantResolver<? super Message<?>>> targetTenantResolver =
            config -> (message, tenantDescriptors) -> tenantDescriptors.stream().findFirst().orElse(TenantDescriptor.tenantWithId("default"));

    private MultiTenantCommandBus multiTenantCommandBus;
    private MultiTenantEventBus multiTenantEventBus;
    private MultiTenantQueryBus multiTenantQueryBus;

    @Override
    public void configureModule(Configurer configurer) {
        configurer.getModuleConfiguration(MultiTenantConnectorConfigurerModule.class)
                .registerTenantsProvider(Collections::emptyList)
                .registerTargetTenantResolver(targetTenantResolver);

        configurer.registerComponent(TenantCommandSegmentFactory.class, tenantCommandSegmentFactory);
        configurer.registerComponent(TenantQuerySegmentFactory.class, tenantQuerySegmentFactory);
        configurer.registerComponent(TenantEventSegmentFactory.class, tenantEventSegmentFactory);

        configurer.registerComponent(TargetTenantResolver.class, targetTenantResolver);

        configurer.configureCommandBus(this::buildMultiTenantCommandBus);
        configurer.configureEventBus(this::buildMultiTenantEventBus);
        configurer.configureQueryBus(this::buildMultiTenantQueryBus);
    }

    @Override
    public void initialize(Configuration config) {
        config.onStart(
                Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS - 1,
                () -> tenantsProvider.get().stream().filter(tenantFilter).forEach(tenantDescriptor -> {
                    multiTenantCommandBus.registerTenant(tenantDescriptor);
                    multiTenantEventBus.registerTenant(tenantDescriptor);
                    multiTenantQueryBus.registerTenant(tenantDescriptor);
                }) //todo - who call registration.cancel?
        );
    }

    private CommandBus buildMultiTenantCommandBus(Configuration config) {
        multiTenantCommandBus = MultiTenantCommandBus.builder()
                .tenantSegmentFactory(config.getComponent(TenantCommandSegmentFactory.class))
                .targetTenantResolver(config.getComponent(TargetTenantResolver.class))
                .build();
        return multiTenantCommandBus;
    }

    private EventBus buildMultiTenantEventBus(Configuration config) {
        multiTenantEventBus = MultiTenantEventBus.builder()
                .tenantSegmentFactory(config.getComponent(TenantEventSegmentFactory.class))
                .targetTenantResolver(config.getComponent(TargetTenantResolver.class))
                .build();
        return multiTenantEventBus;
    }

    private QueryBus buildMultiTenantQueryBus(Configuration config) {
        multiTenantQueryBus = MultiTenantQueryBus.builder()
                .tenantSegmentFactory(config.getComponent(TenantQuerySegmentFactory.class))
                .targetTenantResolver(config.getComponent(TargetTenantResolver.class))
                .build();
        return multiTenantQueryBus;
    }

    /**
     * @param tenantsProvider
     * @return
     */
    public MultiTenantConnectorConfigurerModule registerTenantsProvider(Supplier<List<TenantDescriptor>> tenantsProvider) {
        this.tenantsProvider = tenantsProvider;
        return this;
    }

    /**
     * @param tenantEventSegmentFactory
     * @return
     */
    public MultiTenantConnectorConfigurerModule registerTenantCommandSegmentFactory(
            Function<Configuration, TenantCommandSegmentFactory> tenantEventSegmentFactory
    ) {
        this.tenantCommandSegmentFactory = tenantEventSegmentFactory;
        return this;
    }

    /**
     * @param tenantEventSegmentFactory
     * @return
     */
    public MultiTenantConnectorConfigurerModule registerTenantEventSegmentFactory(
            Function<Configuration, TenantEventSegmentFactory> tenantEventSegmentFactory
    ) {
        this.tenantEventSegmentFactory = tenantEventSegmentFactory;
        return this;
    }

    /**
     * @param tenantQuerySegmentFactory
     * @return
     */
    public MultiTenantConnectorConfigurerModule registerTenantQuerySegmentFactory(
            Function<Configuration, TenantQuerySegmentFactory> tenantQuerySegmentFactory
    ) {
        this.tenantQuerySegmentFactory = tenantQuerySegmentFactory;
        return this;
    }

    /**
     * @param targetTenantResolver
     * @return
     */
    public MultiTenantConnectorConfigurerModule registerTargetTenantResolver(
            Function<Configuration, TargetTenantResolver<? super Message<?>>> targetTenantResolver
    ) {
        this.targetTenantResolver = targetTenantResolver;
        return this;
    }
}
