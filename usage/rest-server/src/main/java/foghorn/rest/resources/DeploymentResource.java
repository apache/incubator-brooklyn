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
package foghorn.rest.resources;

import brooklyn.management.entitlement.EntitlementPredicates;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.rest.resources.AbstractBrooklynRestResource;
import com.google.common.collect.FluentIterable;
import foghorn.rest.api.DeploymentApi;
import foghorn.rest.domain.DeploymentSummary;
import foghorn.rest.transform.DeploymentTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.*;


public class DeploymentResource extends AbstractBrooklynRestResource implements DeploymentApi {

    private static final Logger log = LoggerFactory.getLogger(DeploymentResource.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public List<DeploymentSummary> list() {
        return FluentIterable
                .from(mgmt().getApplications())
                .filter(EntitlementPredicates.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY))
                .transform(DeploymentTransformer.FROM_APPLICATION)
                .toList();
    }

    @Override
    public DeploymentSummary get(String application) {
        return DeploymentTransformer.deploymentSummary(brooklyn().getApplication(application));
    }
}