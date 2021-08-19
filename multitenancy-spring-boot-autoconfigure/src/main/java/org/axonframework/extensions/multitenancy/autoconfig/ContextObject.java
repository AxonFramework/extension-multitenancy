package org.axonframework.extensions.multitenancy.autoconfig;

import java.util.Map;

/**
 * @author Stefan Dragisic
 */


public class ContextObject {

    private String context;
    private Map<String, String> metaData;

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }


    public Map<String, String> getMetaData() {
        return metaData;
    }

    public void setMetaData(Map<String, String> metaData) {
        this.metaData = metaData;
    }
}

