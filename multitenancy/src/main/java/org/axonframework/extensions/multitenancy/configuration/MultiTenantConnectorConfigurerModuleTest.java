package org.axonframework.extensions.multitenancy.configuration;

import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.commandbus.MultiTenantQueryBus;
import org.axonframework.extensions.multitenancy.commandbus.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.commandbus.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.commandbus.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.commandbus.TenantQuerySegmentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Stefan Dragisic
 */
class MultiTenantConnectorConfigurerModuleTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void testDefaultConfigurerLoadsMultiTenantConnectorConfigurerModule() {
        Configuration testSubject = DefaultConfigurer.defaultConfiguration().start();

        assertNotNull(testSubject.getComponent(TenantCommandSegmentFactory.class));
        assertNotNull(testSubject.getComponent(TenantQuerySegmentFactory.class));
        assertNotNull(testSubject.getComponent(TenantEventSegmentFactory.class));
        assertNotNull(testSubject.getComponent(TargetTenantResolver.class));
        assertNotNull(testSubject.getComponent(MultiTenantCommandBus.class));
        assertNotNull(testSubject.getComponent(MultiTenantEventStore.class));
        assertNotNull(testSubject.getComponent(MultiTenantQueryBus.class));
    }

    @Test
    void configureModule() {
    }

    @Test
    void initialize() {
    }
}