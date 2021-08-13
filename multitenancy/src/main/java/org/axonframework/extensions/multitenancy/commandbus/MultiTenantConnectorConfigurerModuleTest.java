package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
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
        assertNotNull(testSubject.getComponent(MultiTenantEventBus.class));
        assertNotNull(testSubject.getComponent(MultiTenantQueryBus.class));
    }

    @Test
    void configureModule() {
    }

    @Test
    void initialize() {
    }
}