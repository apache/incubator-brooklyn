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
package brooklyn.entity.brooklynnode.effector;

import org.apache.http.HttpStatus;

import brooklyn.entity.Effector;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.BrooklynNode.SetHAPriorityEffector;
import brooklyn.entity.brooklynnode.EntityHttpClient;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.http.HttpToolResponse;

import com.google.api.client.util.Preconditions;
import com.google.common.collect.ImmutableMap;

public class SetHAPriorityEffectorBody extends EffectorBody<Integer> implements SetHAPriorityEffector {
    public static final Effector<Integer> SET_HA_PRIORITY = Effectors.effector(SetHAPriorityEffector.SET_HA_PRIORITY).impl(new SetHAPriorityEffectorBody()).build();

    @Override
    public Integer call(ConfigBag parameters) {
        Integer priority = parameters.get(PRIORITY);
        Preconditions.checkNotNull(priority, PRIORITY.getName() + " parameter is required");

        EntityHttpClient httpClient = ((BrooklynNode)entity()).http();
        HttpToolResponse resp = httpClient.post("/v1/server/ha/priority",
            ImmutableMap.of("Brooklyn-Allow-Non-Master-Access", "true"),
            ImmutableMap.of("priority", priority.toString()));

        if (resp.getResponseCode() == HttpStatus.SC_OK) {
            return Integer.valueOf(resp.getContentAsString());
        } else {
            throw new IllegalStateException("Unexpected response code: " + resp.getResponseCode() + "\n" + resp.getContentAsString());
        }
    }

}
