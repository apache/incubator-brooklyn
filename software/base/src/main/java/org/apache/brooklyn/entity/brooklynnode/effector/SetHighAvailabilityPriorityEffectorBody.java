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
package org.apache.brooklyn.entity.brooklynnode.effector;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode;
import org.apache.brooklyn.entity.brooklynnode.EntityHttpClient;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode.SetHighAvailabilityPriorityEffector;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.http.HttpToolResponse;
import org.apache.http.HttpStatus;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class SetHighAvailabilityPriorityEffectorBody extends EffectorBody<Integer> implements SetHighAvailabilityPriorityEffector {
    public static final Effector<Integer> SET_HIGH_AVAILABILITY_PRIORITY = Effectors.effector(SetHighAvailabilityPriorityEffector.SET_HIGH_AVAILABILITY_PRIORITY).impl(new SetHighAvailabilityPriorityEffectorBody()).build();

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
