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
package org.apache.brooklyn.rest.transform;

import java.net.URI;

import org.apache.brooklyn.core.mgmt.internal.AccessManager;
import org.apache.brooklyn.rest.domain.AccessSummary;

import com.google.common.collect.ImmutableMap;
import javax.ws.rs.core.UriBuilder;
import org.apache.brooklyn.rest.api.AccessApi;
import static org.apache.brooklyn.rest.util.WebResourceUtils.resourceUriBuilder;

/**
 * @author Adam Lowe
 */
public class AccessTransformer {

    public static AccessSummary accessSummary(AccessManager manager, UriBuilder ub) {
        URI selfUri = resourceUriBuilder(ub, AccessApi.class).build();
        ImmutableMap<String, URI> links = ImmutableMap.of("self", selfUri);

        return new AccessSummary(manager.isLocationProvisioningAllowed(), links);
    }
}
