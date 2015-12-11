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
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.policy.Policies;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.PolicyConfigSummary;
import org.apache.brooklyn.rest.domain.PolicySummary;
import org.apache.brooklyn.rest.resources.PolicyConfigResource;
import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;

import com.google.common.collect.ImmutableMap;
import javax.ws.rs.core.UriBuilder;
import org.apache.brooklyn.rest.api.ApplicationApi;
import org.apache.brooklyn.rest.api.EntityApi;
import org.apache.brooklyn.rest.api.PolicyApi;
import org.apache.brooklyn.rest.api.PolicyConfigApi;
import static org.apache.brooklyn.rest.util.WebResourceUtils.resourceUriBuilder;
import static org.apache.brooklyn.rest.util.WebResourceUtils.serviceUriBuilder;

/**
 * Converts from Brooklyn entities to restful API summary objects
 */
public class PolicyTransformer {

    public static PolicySummary policySummary(Entity entity, Policy policy, UriBuilder ub) {
        URI applicationUri = serviceUriBuilder(ub, ApplicationApi.class, "get").build(entity.getApplicationId());
        URI entityUri = serviceUriBuilder(ub, EntityApi.class, "get").build(entity.getApplicationId(), entity.getId());
        URI configUri = resourceUriBuilder(ub, PolicyConfigApi.class).build(entity.getApplicationId(), entity.getId(), policy.getId());

        URI selfUri = serviceUriBuilder(ub, PolicyApi.class, "getStatus").build(entity.getApplicationId(), entity.getId(), policy.getId());
        URI startUri = serviceUriBuilder(ub, PolicyApi.class, "start").build(entity.getApplicationId(), entity.getId(), policy.getId());
        URI stopUri = serviceUriBuilder(ub, PolicyApi.class, "stop").build(entity.getApplicationId(), entity.getId(), policy.getId());
        URI destroyUri = serviceUriBuilder(ub, PolicyApi.class, "destroy").build(entity.getApplicationId(), entity.getId(), policy.getId());

        Map<String, URI> links = ImmutableMap.<String, URI>builder()
                .put("self", selfUri)
                .put("config", configUri)
                .put("start", startUri)
                .put("stop", stopUri)
                .put("destroy", destroyUri)
                .put("application", applicationUri)
                .put("entity", entityUri)
                .build();

        return new PolicySummary(policy.getId(), policy.getDisplayName(), policy.getCatalogItemId(), ApplicationTransformer.statusFromLifecycle(Policies.getPolicyStatus(policy)), links);
    }

    public static PolicyConfigSummary policyConfigSummary(BrooklynRestResourceUtils utils, ApplicationSummary application, Entity entity, Policy policy, ConfigKey<?> config, UriBuilder ub) {
        PolicyConfigSummary summary = policyConfigSummary(utils, entity, policy, config, ub);
//        TODO
//        if (!entity.getApplicationId().equals(application.getInstance().getId()))
//            throw new IllegalStateException("Application "+application+" does not match app "+entity.getApplication()+" of "+entity);
        return summary;
    }

    public static PolicyConfigSummary policyConfigSummary(BrooklynRestResourceUtils utils, Entity entity, Policy policy, ConfigKey<?> config, UriBuilder ub) {
        URI applicationUri = serviceUriBuilder(ub, ApplicationApi.class, "get").build(entity.getApplicationId());
        URI entityUri = serviceUriBuilder(ub, EntityApi.class, "get").build(entity.getApplicationId(), entity.getId());
        URI policyUri = serviceUriBuilder(ub, PolicyApi.class, "getStatus").build(entity.getApplicationId(), entity.getId(), policy.getId());
        URI configUri = serviceUriBuilder(ub, PolicyConfigApi.class, "get").build(entity.getApplicationId(), entity.getId(), policy.getId(), config.getName());

        Map<String, URI> links = ImmutableMap.<String, URI>builder()
                .put("self", configUri)
                .put("application", applicationUri)
                .put("entity", entityUri)
                .put("policy", policyUri)
                .build();

        return new PolicyConfigSummary(config.getName(), config.getTypeName(), config.getDescription(), 
                PolicyConfigResource.getStringValueForDisplay(utils, policy, config.getDefaultValue()), 
                config.isReconfigurable(), 
                links);
    }
}
