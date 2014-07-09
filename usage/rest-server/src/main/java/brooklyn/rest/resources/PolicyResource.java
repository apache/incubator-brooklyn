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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.Policies;
import brooklyn.rest.api.PolicyApi;
import brooklyn.rest.domain.PolicySummary;
import brooklyn.rest.domain.Status;
import brooklyn.rest.domain.SummaryComparators;
import brooklyn.rest.transform.ApplicationTransformer;
import brooklyn.rest.transform.PolicyTransformer;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;

public class PolicyResource extends AbstractBrooklynRestResource implements PolicyApi {

    private static final Logger log = LoggerFactory.getLogger(PolicyResource.class);

    @Override
    public List<PolicySummary> list( final String application, final String entityToken) {
        final Entity entity = brooklyn().getEntity(application, entityToken);
        return FluentIterable.from(entity.getPolicies())
            .transform(new Function<Policy, PolicySummary>() {
                @Override
                public PolicySummary apply(Policy policy) {
                    return PolicyTransformer.policySummary(entity, policy);
                }
            })
            .toSortedList(SummaryComparators.nameComparator());
    }

    // TODO support parameters  ?show=value,summary&name=xxx
    // (and in sensors class)
    @Override
    public Map<String, Boolean> batchConfigRead( String application, String entityToken) {
        // TODO: add test
        EntityLocal entity = brooklyn().getEntity(application, entityToken);
        Map<String, Boolean> result = Maps.newLinkedHashMap();
        for (Policy p : entity.getPolicies()) {
            result.put(p.getId(), !p.isSuspended());
        }
        return result;
    }

    @Override
    public String addPolicy( String application,String entityToken, String policyTypeName,
            // TODO would like to make this optional but jersey complains if we do
            Map<String, String> config
    ) {
        EntityLocal entity = brooklyn().getEntity(application, entityToken);

        try {
            Class<?> policyType = Class.forName(policyTypeName);
            Policy policy = (Policy) policyType.newInstance();
            if (config != null && !config.isEmpty()) {
                // TODO support this:
                //policy.setConfig(config);
                policyType.getMethod("setConfig", Map.class).invoke(policy, config);
            }
            log.debug("REST API adding policy " + policy + " to " + entity);
            entity.addPolicy(policy);
            return policy.getId();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public Status getStatus(String application, String entityToken, String policyId) {
        Policy policy = brooklyn().getPolicy(application, entityToken, policyId);
        return ApplicationTransformer.statusFromLifecycle(Policies.getPolicyStatus(policy));
    }

    @Override
    public Response start( String application, String entityToken, String policyId) {
        Policy policy = brooklyn().getPolicy(application, entityToken, policyId);

        policy.resume();
        return Response.status(Response.Status.OK).build();
    }

    @Override
    public Response stop(String application, String entityToken, String policyId) {
        Policy policy = brooklyn().getPolicy(application, entityToken, policyId);

        policy.suspend();
        return Response.status(Response.Status.OK).build();
    }

    @Override
    public Response destroy(String application, String entityToken, String policyToken) {
        EntityLocal entity = brooklyn().getEntity(application, entityToken);
        Policy policy = brooklyn().getPolicy(entity, policyToken);

        policy.suspend();
        entity.removePolicy(policy);
        return Response.status(Response.Status.OK).build();
    }
}
