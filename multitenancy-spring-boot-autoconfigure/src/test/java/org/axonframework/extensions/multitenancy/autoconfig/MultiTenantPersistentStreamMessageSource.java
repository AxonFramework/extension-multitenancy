package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.common.Registration;
import org.axonframework.config.Configuration;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MultiTenantPersistentStreamMessageSourceTest {

    @Mock
    private Configuration configuration;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private AxonServerConfiguration.PersistentStreamProcessorSettings settings;

    @Mock
    private TenantPersistentStreamMessageSourceFactory tenantPersistentStreamMessageSourceFactory;

    private MultiTenantPersistentStreamMessageSource source;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(settings.getName()).thenReturn("testName");
        when(settings.getInitialSegmentCount()).thenReturn(1);
        when(settings.getBatchSize()).thenReturn(100);
        when(settings.getSequencingPolicy()).thenReturn("testSequencingPolicy"); // Add this line
        when(settings.getSequencingPolicyParameters()).thenReturn(Collections.emptyList()); // Add this line
        when(settings.getInitial()).thenReturn(0); // Add this line, or provide a suitable initial value
        when(settings.getFilter()).thenReturn(null); // Add this line, or provide a suitable filter

        source = new MultiTenantPersistentStreamMessageSource(
                configuration,
                scheduledExecutorService,
                "testName",
                settings,
                tenantPersistentStreamMessageSourceFactory
        );
    }

    @Test
    void registerTenant() {
        TenantDescriptor tenantDescriptor = mock(TenantDescriptor.class);
        PersistentStreamMessageSource tenantSource = mock(PersistentStreamMessageSource.class);

        when(tenantPersistentStreamMessageSourceFactory.build(anyString(), any(), any(), any()))
                .thenReturn(tenantSource);

        Registration registration = source.registerTenant(tenantDescriptor);

        assertThat(registration).isNotNull();
        assertThat(source.tenantSegments()).containsKey(tenantDescriptor);
        assertThat(source.tenantSegments().get(tenantDescriptor)).isEqualTo(tenantSource);

        verify(tenantPersistentStreamMessageSourceFactory).build(eq("testName"), eq(settings), eq(tenantDescriptor), eq(configuration));
    }

    @Test
    void registerAndStartTenant() {
        TenantDescriptor tenantDescriptor = mock(TenantDescriptor.class);
        PersistentStreamMessageSource tenantSource = mock(PersistentStreamMessageSource.class);

        when(tenantPersistentStreamMessageSourceFactory.build(anyString(), any(), any(), any()))
                .thenReturn(tenantSource);

        Registration registration = source.registerAndStartTenant(tenantDescriptor);

        assertThat(registration).isNotNull();
        assertThat(source.tenantSegments()).containsKey(tenantDescriptor);
        assertThat(source.tenantSegments().get(tenantDescriptor)).isEqualTo(tenantSource);

        verify(tenantPersistentStreamMessageSourceFactory).build(eq("testName"), eq(settings), eq(tenantDescriptor), eq(configuration));
    }

    @Test
    void tenantSegments() {
        TenantDescriptor tenantDescriptor1 = mock(TenantDescriptor.class);
        TenantDescriptor tenantDescriptor2 = mock(TenantDescriptor.class);
        PersistentStreamMessageSource tenantSource1 = mock(PersistentStreamMessageSource.class);
        PersistentStreamMessageSource tenantSource2 = mock(PersistentStreamMessageSource.class);

        when(tenantPersistentStreamMessageSourceFactory.build(anyString(), any(), eq(tenantDescriptor1), any()))
                .thenReturn(tenantSource1);
        when(tenantPersistentStreamMessageSourceFactory.build(anyString(), any(), eq(tenantDescriptor2), any()))
                .thenReturn(tenantSource2);

        source.registerTenant(tenantDescriptor1);
        source.registerTenant(tenantDescriptor2);

        Map<TenantDescriptor, PersistentStreamMessageSource> segments = source.tenantSegments();

        assertThat(segments).hasSize(2);
        assertThat(segments).containsEntry(tenantDescriptor1, tenantSource1);
        assertThat(segments).containsEntry(tenantDescriptor2, tenantSource2);
    }
}