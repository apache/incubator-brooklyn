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
package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.Policies;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.PolicyConfigSummary;
import brooklyn.rest.domain.PolicySummary;
import brooklyn.rest.resources.PolicyConfigResource;
import brooklyn.rest.util.BrooklynRestResourceUtils;

import com.google.common.collect.ImmutableMap;

/**
 * Converts from Brooklyn entities to restful API summary objects
 */
public class PolicyTransformer {
//    private static final org.slf4j.Logger log = LoggerFactory.getLogger(PolicyTransformer.class);

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

        return new PolicySummary(policy.getId(), policy.getDisplayName(), ApplicationTransformer.statusFromLifecycle(Policies.getPolicyStatus(policy)), links);
    }

    public static PolicyConfigSummary policyConfigSummary(BrooklynRestResourceUtils utils, ApplicationSummary application, EntityLocal entity, Policy policy, ConfigKey<?> config) {
        PolicyConfigSummary summary = policyConfigSummary(utils, entity, policy, config);
//        TODO
//        if (!entity.getApplicationId().equals(application.getInstance().getId()))
//            throw new IllegalStateException("Application "+application+" does not match app "+entity.getApplication()+" of "+entity);
        return summary;
    }

    public static PolicyConfigSummary policyConfigSummary(BrooklynRestResourceUtils utils, EntityLocal entity, Policy policy, ConfigKey<?> config) {
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
