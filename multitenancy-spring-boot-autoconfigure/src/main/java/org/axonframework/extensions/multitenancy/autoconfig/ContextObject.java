/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

