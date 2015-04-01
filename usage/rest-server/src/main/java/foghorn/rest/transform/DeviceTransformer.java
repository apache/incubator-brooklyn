/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package foghorn.rest.transform;

import brooklyn.entity.Entity;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import foghorn.rest.domain.DeviceSummary;

import java.net.URI;

/**
 *
 */
public class DeviceTransformer {

    public static final Function<? super Entity, DeviceSummary> FROM_ENTITY = new Function<Entity, DeviceSummary>() {
        @Override
        public DeviceSummary apply(Entity entity) {
            return DeviceTransformer.deviceSummary(entity);
        }
    };

    //TODO type checking
    public static DeviceSummary deviceSummary(Entity entity) {
        String deploymentUri = "/v1/deployments/" + entity.getApplicationId();
        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String deviceUri = deploymentUri + "/devices/" + entity.getId();
        String entityUrl = applicationUri + "/entities/" + entity.getId();
        String type = entity.getEntityType().getName();
        ImmutableMap.Builder<String, URI> lb = ImmutableMap.<String, URI>builder()
                .put("self", URI.create(deviceUri))
                .put("deployment", URI.create(deploymentUri))
                .put("config", URI.create(entityUrl + "/config"))
                .put("metrics", URI.create(entityUrl + "/sensors"))
                .put("actions", URI.create(entityUrl + "/effectors"))
                .put("activities", URI.create(entityUrl + "/activities"))
                .put("services", URI.create(deviceUri + "/services"))
            ;
        if (entity.getIconUrl()!=null)
            lb.put("iconUrl", URI.create(entityUrl + "/icon"));
        return new DeviceSummary(entity.getId(), entity.getDisplayName(), type, entity.getCatalogItemId(), lb.build());
    }
}
