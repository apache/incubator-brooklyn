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
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.resources.AbstractBrooklynRestResource;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.util.WebResourceUtils;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import foghorn.rest.api.DeviceApi;
import foghorn.rest.api.ServiceApi;
import foghorn.rest.domain.DeviceSummary;
import foghorn.rest.domain.ServiceSummary;
import foghorn.rest.transform.ServiceTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.List;


public class ServiceResource extends AbstractBrooklynRestResource implements ServiceApi {

    private static final Logger log = LoggerFactory.getLogger(ServiceResource.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public List<ServiceSummary> list(final String deployment, String device) {
        return FluentIterable
                .from(brooklyn().getEntity(deployment, device).getChildren())
                .filter(EntitlementPredicates.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY))
                .transform(ServiceTransformer.FROM_ENTITY)
                .toList();
    }

    @Override
    public ServiceSummary get(String deployment, String device, String service) {
        Entity entity = brooklyn().getEntity(deployment, service);
        if (Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
            return ServiceTransformer.serviceSummary(entity);
        }
        throw WebResourceUtils.unauthorized("User '%s' is not authorized to get service '%s'",
                Entitlements.getEntitlementContext().user(), entity);
    }
}