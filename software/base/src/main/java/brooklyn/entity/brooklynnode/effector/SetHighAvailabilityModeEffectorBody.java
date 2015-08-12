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

import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.http.HttpStatus;

import brooklyn.entity.Effector;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.BrooklynNode.SetHighAvailabilityModeEffector;
import brooklyn.entity.brooklynnode.EntityHttpClient;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Functionals;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.javalang.Enums;

import com.google.common.base.Preconditions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;

public class SetHighAvailabilityModeEffectorBody extends EffectorBody<ManagementNodeState> implements SetHighAvailabilityModeEffector {
    public static final Effector<ManagementNodeState> SET_HIGH_AVAILABILITY_MODE = Effectors.effector(SetHighAvailabilityModeEffector.SET_HIGH_AVAILABILITY_MODE).impl(new SetHighAvailabilityModeEffectorBody()).build();

    @Override
    public ManagementNodeState call(ConfigBag parameters) {
        HighAvailabilityMode mode = parameters.get(MODE);
        Preconditions.checkNotNull(mode, MODE.getName() + " parameter is required");

        EntityHttpClient httpClient = ((BrooklynNode)entity()).http();
        HttpToolResponse resp = httpClient.post("/v1/server/ha/state", 
                ImmutableMap.of("Brooklyn-Allow-Non-Master-Access", "true"),
                ImmutableMap.of("mode", mode.toString()));

        if (resp.getResponseCode() == HttpStatus.SC_OK) {
            Function<HttpToolResponse, ManagementNodeState> parseRespone = Functionals.chain(
                    Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.cast(String.class)),
                    Enums.fromStringFunction(ManagementNodeState.class));
            return parseRespone.apply(resp);
        } else {
            throw new IllegalStateException("Unexpected response code: " + resp.getResponseCode() + "\n" + resp.getContentAsString());
        }
    }
}
