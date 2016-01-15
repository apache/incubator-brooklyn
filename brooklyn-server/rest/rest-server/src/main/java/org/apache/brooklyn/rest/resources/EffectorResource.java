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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements.StringAndArgument;
import org.apache.brooklyn.core.mgmt.internal.EffectorUtils;
import org.apache.brooklyn.rest.api.EffectorApi;
import org.apache.brooklyn.rest.domain.EffectorSummary;
import org.apache.brooklyn.rest.domain.SummaryComparators;
import org.apache.brooklyn.rest.filter.HaHotStateRequired;
import org.apache.brooklyn.rest.transform.EffectorTransformer;
import org.apache.brooklyn.rest.transform.TaskTransformer;
import org.apache.brooklyn.rest.util.WebResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

@HaHotStateRequired
public class EffectorResource extends AbstractBrooklynRestResource implements EffectorApi {

    private static final Logger log = LoggerFactory.getLogger(EffectorResource.class);

    @Override
    public List<EffectorSummary> list(final String application, final String entityToken) {
        final Entity entity = brooklyn().getEntity(application, entityToken);
        return FluentIterable
                .from(entity.getEntityType().getEffectors())
                .filter(new Predicate<Effector<?>>() {
                    @Override
                    public boolean apply(@Nullable Effector<?> input) {
                        return Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.INVOKE_EFFECTOR,
                                Entitlements.EntityAndItem.of(entity, StringAndArgument.of(input.getName(), null)));
                    }
                })
                .transform(new Function<Effector<?>, EffectorSummary>() {
                    @Override
                    public EffectorSummary apply(Effector<?> effector) {
                        return EffectorTransformer.effectorSummary(entity, effector);
                    }
                })
                .toSortedList(SummaryComparators.nameComparator());
    }

    @Override
    public Response invoke(String application, String entityToken, String effectorName,
            String timeout, Map<String, Object> parameters) {
        final Entity entity = brooklyn().getEntity(application, entityToken);

        // TODO check effectors?
        Maybe<Effector<?>> effector = EffectorUtils.findEffectorDeclared(entity, effectorName);
        if (effector.isAbsentOrNull()) {
            throw WebResourceUtils.notFound("Entity '%s' has no effector with name '%s'", entityToken, effectorName);
        } else if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.INVOKE_EFFECTOR,
                Entitlements.EntityAndItem.of(entity, StringAndArgument.of(effector.get().getName(), null)))) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to invoke effector %s on entity %s",
                    Entitlements.getEntitlementContext().user(), effector.get().getName(), entity);
        }
        log.info("REST invocation of " + entity + "." + effector.get() + " " + parameters);
        Task<?> t = entity.invoke(effector.get(), parameters);

        try {
            Object result;
            if (timeout == null || timeout.isEmpty() || "never".equalsIgnoreCase(timeout)) {
                result = t.get();
            } else {
                long timeoutMillis = "always".equalsIgnoreCase(timeout) ? 0 : Time.parseElapsedTime(timeout);
                try {
                    if (timeoutMillis == 0) throw new TimeoutException();
                    result = t.get(timeoutMillis, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    result = TaskTransformer.taskSummary(t);
                }
            }
            return Response.status(Response.Status.ACCEPTED).entity(result).build();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

}
