package org.axonframework.extensions.multitenancy.autoconfig;

import java.util.Map;

/**
 * DTO for the context object retrived from Axon Server
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class ContextObject {

    private String context;
    private Map<String, String> metaData;
    private String replicationGroup;

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }


    public Map<String, String> getMetaData() {
        metaData.putIfAbsent("replicationGroup", replicationGroup);
        return metaData;
    }

    public void setMetaData(Map<String, String> metaData) {
        this.metaData = metaData;
    }

    public String getReplicationGroup() {
        return replicationGroup;
    }

    public void setReplicationGroup(String replicationGroup) {
        this.replicationGroup = replicationGroup;
    }
}

