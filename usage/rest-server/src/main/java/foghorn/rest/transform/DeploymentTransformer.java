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

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.rest.transform.ApplicationTransformer;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import foghorn.rest.domain.DeploymentSummary;
import foghorn.rest.domain.DeviceSummary;

import java.net.URI;

/**
 *
 */
public class DeploymentTransformer {

    public static final Function<? super Application, DeploymentSummary> FROM_APPLICATION =
            new Function<Application, DeploymentSummary>() {

        @Override
        public DeploymentSummary apply(Application application) {
            return DeploymentTransformer.deploymentSummary(application);
        }
    };

    //TODO type checking
    public static DeploymentSummary deploymentSummary(Application application) {
        String deploymentUri = "/v1/deployments/" + application.getApplicationId();
        String devicesUri = deploymentUri + "/devices";
        ImmutableMap.Builder<String, URI> lb = ImmutableMap.<String, URI>builder()
                .put("self", URI.create(deploymentUri))
                .put("devices", URI.create(devicesUri))
            ;
        return new DeploymentSummary(application.getApplicationId(),
                ApplicationTransformer.specFromApplication(application),
                ApplicationTransformer.statusFromApplication(application),
                lb.build());
    }
}
