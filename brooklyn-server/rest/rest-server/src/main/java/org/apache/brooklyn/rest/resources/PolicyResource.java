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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.policy.Policies;
import org.apache.brooklyn.rest.api.PolicyApi;
import org.apache.brooklyn.rest.domain.PolicySummary;
import org.apache.brooklyn.rest.domain.Status;
import org.apache.brooklyn.rest.domain.SummaryComparators;
import org.apache.brooklyn.rest.filter.HaHotStateRequired;
import org.apache.brooklyn.rest.transform.ApplicationTransformer;
import org.apache.brooklyn.rest.transform.PolicyTransformer;
import org.apache.brooklyn.rest.util.WebResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;

@HaHotStateRequired
public class PolicyResource extends AbstractBrooklynRestResource implements PolicyApi {

    private static final Logger log = LoggerFactory.getLogger(PolicyResource.class);

    @Override
    public List<PolicySummary> list( final String application, final String entityToken) {
        final Entity entity = brooklyn().getEntity(application, entityToken);
        return FluentIterable.from(entity.policies())
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
        Entity entity = brooklyn().getEntity(application, entityToken);
        Map<String, Boolean> result = Maps.newLinkedHashMap();
        for (Policy p : entity.policies()) {
            result.put(p.getId(), !p.isSuspended());
        }
        return result;
    }

    // TODO would like to make 'config' arg optional but jersey complains if we do
    @SuppressWarnings("unchecked")
    @Override
    public PolicySummary addPolicy( String application,String entityToken, String policyTypeName,
            Map<String, String> config) {
        Entity entity = brooklyn().getEntity(application, entityToken);
        Class<? extends Policy> policyType;
        try {
            policyType = (Class<? extends Policy>) Class.forName(policyTypeName);
        } catch (ClassNotFoundException e) {
            throw WebResourceUtils.badRequest("No policy with type %s found", policyTypeName);
        } catch (ClassCastException e) {
            throw WebResourceUtils.badRequest("No policy with type %s found", policyTypeName);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }

        Policy policy = entity.policies().add(PolicySpec.create(policyType).configure(config));
        log.debug("REST API added policy " + policy + " to " + entity);

        return PolicyTransformer.policySummary(entity, policy);
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
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @Override
    public Response stop(String application, String entityToken, String policyId) {
        Policy policy = brooklyn().getPolicy(application, entityToken, policyId);

        policy.suspend();
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @Override
    public Response destroy(String application, String entityToken, String policyToken) {
        Entity entity = brooklyn().getEntity(application, entityToken);
        Policy policy = brooklyn().getPolicy(entity, policyToken);

        policy.suspend();
        entity.policies().remove(policy);
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
