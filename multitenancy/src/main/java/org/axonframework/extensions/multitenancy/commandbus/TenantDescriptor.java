package org.axonframework.extensions.multitenancy.commandbus;

import java.util.Map;

public interface TenantDescriptor {

    String tenantId();

    String tenantName();

    Map<String, String> properties();

}
