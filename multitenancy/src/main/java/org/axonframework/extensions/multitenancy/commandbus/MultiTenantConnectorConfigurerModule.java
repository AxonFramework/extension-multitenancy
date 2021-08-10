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
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Steven van Beelen
 * @author Stefan Dragisic
 */
public class MultiTenantConnectorConfigurerModule implements ConfigurerModule, ModuleConfiguration {//todo check it out vs ConfigurerModule

    private Supplier<List<TenantDescriptor>> tenantsProvider;
    private Function<Configuration, TenantSegmentFactory> tenantSegmentFactory =
            config -> tenantDescriptor -> SimpleCommandBus.builder()
                    .duplicateCommandHandlerResolver(
                            config.getComponent(DuplicateCommandHandlerResolver.class,
                                    LoggingDuplicateCommandHandlerResolver::instance))
                    .messageMonitor(config.messageMonitor(CommandBus.class, "commandBus"))
                    .transactionManager(config.getComponent(TransactionManager.class, NoTransactionManager::instance))
                    .build();
    private Function<Configuration, TargetTenantResolver<? super Message<?>>> targetTenantResolver =
            config -> message -> "default"; //todo

    private MultiTenantCommandBus multiTenantCommandBus;

    @Override
    public void configureModule(Configurer configurer) {
        configurer.getModuleConfiguration(MultiTenantConnectorConfigurerModule.class)
                .registerTenantsProvider(() -> null) //todo empty list or default context
                .registerTargetTenantResolver(null); //todo check target context resolver works

        configurer.registerComponent(TenantSegmentFactory.class, tenantSegmentFactory);
        configurer.registerComponent(TargetTenantResolver.class, targetTenantResolver);
        configurer.configureCommandBus(this::buildMultiTenantCommandBus);
    }

    @Override
    public void initialize(Configuration config) {
        config.onStart(
                Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS - 1,
                () -> tenantsProvider.get().forEach(multiTenantCommandBus::registerTenant)
        );
    }

    private CommandBus buildMultiTenantCommandBus(Configuration config) {
        multiTenantCommandBus = MultiTenantCommandBus.builder()
                .tenantSegmentFactory(config.getComponent(TenantSegmentFactory.class))
                .targetTenantResolver(config.getComponent(TargetTenantResolver.class))
                .build();
        return multiTenantCommandBus;
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
     * @param tenantSegmentFactory
     * @return
     */
    public MultiTenantConnectorConfigurerModule registerTenantSegmentFactory(
            Function<Configuration, TenantSegmentFactory> tenantSegmentFactory
    ) {
        this.tenantSegmentFactory = tenantSegmentFactory;
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
