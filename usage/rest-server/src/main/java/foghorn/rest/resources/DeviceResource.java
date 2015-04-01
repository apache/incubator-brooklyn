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

import brooklyn.entity.Entity;
import brooklyn.management.entitlement.EntitlementPredicates;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.rest.resources.AbstractBrooklynRestResource;
import brooklyn.rest.util.WebResourceUtils;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import foghorn.rest.api.DeviceApi;
import foghorn.rest.domain.DeviceSummary;
import foghorn.rest.transform.DeviceTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.List;


public class DeviceResource extends AbstractBrooklynRestResource implements DeviceApi {

    private static final Logger log = LoggerFactory.getLogger(DeviceResource.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public List<DeviceSummary> list(final String deployment) {
        return FluentIterable
                .from(brooklyn().getApplication(deployment).getDescendants())
                .filter(new Predicate<Entity>() {
                    @Override
                    public boolean apply(Entity t) {
                        //TODO add foghorn entities as dependency
                        return t.getEntityType().getName().equals("com.foghorn.entity.FoghornEdgeStack");
                    }
                })
                .filter(EntitlementPredicates.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY))
                .transform(DeviceTransformer.FROM_ENTITY)
                .toList();
    }

    @Override
    public DeviceSummary get(String deployment, String deviceName) {
        Entity entity = brooklyn().getEntity(deployment, deviceName);
        if (Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
            return DeviceTransformer.deviceSummary(entity);
        }
        throw WebResourceUtils.unauthorized("User '%s' is not authorized to get device '%s'",
                Entitlements.getEntitlementContext().user(), entity);
    }
}