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

/**
 * Converts from Brooklyn entities to restful API summary objects
 */
public class PolicyTransformer {

    public static PolicySummary policySummary(Entity entity, Policy policy) {
        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();

        Map<String, URI> links = ImmutableMap.<String, URI>builder()
                .put("self", URI.create(entityUri + "/policies/" + policy.getId()))
                .put("config", URI.create(entityUri + "/policies/" + policy.getId() + "/config"))
                .put("start", URI.create(entityUri + "/policies/" + policy.getId() + "/start"))
                .put("stop", URI.create(entityUri + "/policies/" + policy.getId() + "/stop"))
                .put("destroy", URI.create(entityUri + "/policies/" + policy.getId() + "/destroy"))
                .put("application", URI.create(applicationUri))
                .put("entity", URI.create(entityUri))
                .build();

        return new PolicySummary(policy.getId(), policy.getDisplayName(), policy.getCatalogItemId(), ApplicationTransformer.statusFromLifecycle(Policies.getPolicyStatus(policy)), links);
    }

    public static PolicyConfigSummary policyConfigSummary(BrooklynRestResourceUtils utils, ApplicationSummary application, Entity entity, Policy policy, ConfigKey<?> config) {
        PolicyConfigSummary summary = policyConfigSummary(utils, entity, policy, config);
//        TODO
//        if (!entity.getApplicationId().equals(application.getInstance().getId()))
//            throw new IllegalStateException("Application "+application+" does not match app "+entity.getApplication()+" of "+entity);
        return summary;
    }

    public static PolicyConfigSummary policyConfigSummary(BrooklynRestResourceUtils utils, Entity entity, Policy policy, ConfigKey<?> config) {
        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();
        String policyUri = entityUri + "/policies/" + policy.getId();

        Map<String, URI> links = ImmutableMap.<String, URI>builder()
                .put("self", URI.create(policyUri + "/config/" + config.getName()))
                .put("application", URI.create(applicationUri))
                .put("entity", URI.create(entityUri))
                .put("policy", URI.create(policyUri))
                .build();

        return new PolicyConfigSummary(config.getName(), config.getTypeName(), config.getDescription(), 
                PolicyConfigResource.getStringValueForDisplay(utils, policy, config.getDefaultValue()), 
                config.isReconfigurable(), 
                links);
    }
}
