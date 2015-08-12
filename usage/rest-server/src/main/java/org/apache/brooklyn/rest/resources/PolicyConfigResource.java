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
package org.apache.brooklyn.rest.resources;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import brooklyn.basic.BrooklynObjectInternal;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.entitlement.Entitlements;

import org.apache.brooklyn.policy.Policy;
import org.apache.brooklyn.rest.api.PolicyConfigApi;
import org.apache.brooklyn.rest.domain.PolicyConfigSummary;
import org.apache.brooklyn.rest.filter.HaHotStateRequired;
import org.apache.brooklyn.rest.transform.PolicyTransformer;
import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;
import org.apache.brooklyn.rest.util.WebResourceUtils;

import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@HaHotStateRequired
public class PolicyConfigResource extends AbstractBrooklynRestResource implements PolicyConfigApi {

    @Override
    public List<PolicyConfigSummary> list(
            final String application, final String entityToken, final String policyToken) {
        EntityLocal entity = brooklyn().getEntity(application, entityToken);
        Policy policy = brooklyn().getPolicy(entity, policyToken);

        List<PolicyConfigSummary> result = Lists.newArrayList();
        for (ConfigKey<?> key : policy.getPolicyType().getConfigKeys()) {
            result.add(PolicyTransformer.policyConfigSummary(brooklyn(), entity, policy, key));
        }
        return result;
    }

    // TODO support parameters  ?show=value,summary&name=xxx &format={string,json,xml}
    // (and in sensors class)
    @Override
    public Map<String, Object> batchConfigRead(String application, String entityToken, String policyToken) {
        // TODO: add test
        Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
        Map<String, Object> source = ((BrooklynObjectInternal)policy).config().getBag().getAllConfig();
        Map<String, Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<String, Object> ek : source.entrySet()) {
            result.put(ek.getKey(), getStringValueForDisplay(brooklyn(), policy, ek.getValue()));
        }
        return result;
    }

    @Override
    public String get(String application, String entityToken, String policyToken, String configKeyName) {
        Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
        ConfigKey<?> ck = policy.getPolicyType().getConfigKey(configKeyName);
        if (ck == null) throw WebResourceUtils.notFound("Cannot find config key '%s' in policy '%s' of entity '%s'", configKeyName, policy, entityToken);

        return getStringValueForDisplay(brooklyn(), policy, policy.getConfig(ck));
    }

    @Override
    @Deprecated
    public Response set(String application, String entityToken, String policyToken, String configKeyName, String value) {
        return set(application, entityToken, policyToken, configKeyName, (Object) value);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Response set(String application, String entityToken, String policyToken, String configKeyName, Object value) {
        Entity entity = brooklyn().getEntity(application, entityToken);
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.MODIFY_ENTITY, entity)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to modify entity '%s'",
                    Entitlements.getEntitlementContext().user(), entity);
        }

        Policy policy = brooklyn().getPolicy(application, entityToken, policyToken);
        ConfigKey<?> ck = policy.getPolicyType().getConfigKey(configKeyName);
        if (ck == null) throw WebResourceUtils.notFound("Cannot find config key '%s' in policy '%s' of entity '%s'", configKeyName, policy, entityToken);

        policy.config().set((ConfigKey) ck, TypeCoercions.coerce(value, ck.getTypeToken()));

        return Response.status(Response.Status.OK).build();
    }

    public static String getStringValueForDisplay(BrooklynRestResourceUtils utils, Policy policy, Object value) {
        return utils.getStringValueForDisplay(value);
    }
}
