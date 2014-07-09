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
package brooklyn.rest.resources;

import javax.ws.rs.core.Response;

import brooklyn.management.internal.AccessManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.api.AccessApi;
import brooklyn.rest.domain.AccessSummary;
import brooklyn.rest.transform.AccessTransformer;

import com.google.common.annotations.Beta;

@Beta
public class AccessResource extends AbstractBrooklynRestResource implements AccessApi {

    @Override
    public AccessSummary get() {
        AccessManager accessManager = ((ManagementContextInternal) mgmt()).getAccessManager();
        return AccessTransformer.accessSummary(accessManager);
    }

    @Override
    public Response locationProvisioningAllowed(boolean allowed) {
        AccessManager accessManager = ((ManagementContextInternal) mgmt()).getAccessManager();
        accessManager.setLocationProvisioningAllowed(allowed);
        return Response.status(Response.Status.OK).build();
    }
}
