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

import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.ACCEPTED;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTags;
import brooklyn.entity.basic.BrooklynTags.NamedStringTag;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;
import brooklyn.management.entitlement.EntitlementPredicates;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.internal.EntityManagementUtils;
import brooklyn.management.internal.EntityManagementUtils.CreationResult;

import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.rest.api.EntityApi;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.rest.domain.LocationSummary;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.rest.filter.HaHotStateRequired;
import org.apache.brooklyn.rest.transform.EntityTransformer;
import org.apache.brooklyn.rest.transform.LocationTransformer;
import org.apache.brooklyn.rest.transform.LocationTransformer.LocationDetailLevel;
import org.apache.brooklyn.rest.transform.TaskTransformer;
import org.apache.brooklyn.rest.util.WebResourceUtils;

import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.time.Duration;

import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

@HaHotStateRequired
public class EntityResource extends AbstractBrooklynRestResource implements EntityApi {

    private static final Logger log = LoggerFactory.getLogger(EntityResource.class);

    @Context
    private UriInfo uriInfo;
    
    @Override
    public List<EntitySummary> list(final String application) {
        return FluentIterable
                .from(brooklyn().getApplication(application).getChildren())
                .filter(EntitlementPredicates.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY))
                .transform(EntityTransformer.FROM_ENTITY)
                .toList();
    }

    @Override
    public EntitySummary get(String application, String entityName) {
        Entity entity = brooklyn().getEntity(application, entityName);
        if (Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
            return EntityTransformer.entitySummary(entity);
        }
        throw WebResourceUtils.unauthorized("User '%s' is not authorized to get entity '%s'",
                Entitlements.getEntitlementContext().user(), entity);
    }

    @Override
    public List<EntitySummary> getChildren(final String application, final String entity) {
        return FluentIterable
                .from(brooklyn().getEntity(application, entity).getChildren())
                .filter(EntitlementPredicates.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY))
                .transform(EntityTransformer.FROM_ENTITY)
                .toList();
    }

    @Override
    public List<EntitySummary> getChildrenOld(String application, String entity) {
        log.warn("Using deprecated call to /entities when /children should be used");
        return getChildren(application, entity);
    }

    @Override
    public Response addChildren(String applicationToken, String entityToken, Boolean start, String timeoutS, String yaml) {
        final EntityLocal parent = brooklyn().getEntity(applicationToken, entityToken);
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.MODIFY_ENTITY, parent)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to modify entity '%s'",
                    Entitlements.getEntitlementContext().user(), entityToken);
        }
        CreationResult<List<Entity>, List<String>> added = EntityManagementUtils.addChildren(parent, yaml, start)
                .blockUntilComplete(timeoutS==null ? Duration.millis(20) : Duration.of(timeoutS));
        ResponseBuilder response;
        
        if (added.get().size()==1) {
            Entity child = Iterables.getOnlyElement(added.get());
            URI ref = uriInfo.getBaseUriBuilder()
                    .path(EntityApi.class)
                    .path(EntityApi.class, "get")
                    .build(child.getApplicationId(), child.getId());
            response = created(ref);
        } else {
            response = Response.status(Status.CREATED);
        }
        return response.entity(TaskTransformer.taskSummary(added.task())).build();
    }

    @Override
    public List<TaskSummary> listTasks(String applicationId, String entityId) {
        Entity entity = brooklyn().getEntity(applicationId, entityId);
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt().getExecutionManager(), entity);
        return new LinkedList<TaskSummary>(Collections2.transform(tasks, TaskTransformer.FROM_TASK));
    }

    @Override
    public TaskSummary getTask(final String application, final String entityToken, String taskId) {
        // TODO deprecate in favour of ActivityApi.get ?
        Task<?> t = mgmt().getExecutionManager().getTask(taskId);
        if (t == null)
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        return TaskTransformer.FROM_TASK.apply(t);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Object> listTags(String applicationId, String entityId) {
        Entity entity = brooklyn().getEntity(applicationId, entityId);
        return (List<Object>) resolving(MutableList.copyOf(entity.tags().getTags())).preferJson(true).resolve();
    }

    @Override
    public Response getIcon(String applicationId, String entityId) {
        EntityLocal entity = brooklyn().getEntity(applicationId, entityId);
        String url = entity.getIconUrl();
        if (url == null)
            return Response.status(Status.NO_CONTENT).build();

        if (brooklyn().isUrlServerSideAndSafe(url)) {
            // classpath URL's we will serve IF they end with a recognised image format;
            // paths (ie non-protocol) and
            // NB, for security, file URL's are NOT served
            MediaType mime = WebResourceUtils.getImageMediaTypeFromExtension(Files.getFileExtension(url));
            Object content = ResourceUtils.create(brooklyn().getCatalogClassLoader()).getResourceFromUrl(url);
            return Response.ok(content, mime).build();
        }

        // for anything else we do a redirect (e.g. http / https; perhaps ftp)
        return Response.temporaryRedirect(URI.create(url)).build();
    }

    @Override
    public Response rename(String application, String entity, String newName) {
        EntityLocal entityLocal = brooklyn().getEntity(application, entity);
        entityLocal.setDisplayName(newName);
        return status(Response.Status.OK).build();
    }

    @Override
    public Response expunge(String application, String entity, boolean release) {
        EntityLocal entityLocal = brooklyn().getEntity(application, entity);
        Task<?> task = brooklyn().expunge(entityLocal, release);
        TaskSummary summary = TaskTransformer.FROM_TASK.apply(task);
        return status(ACCEPTED).entity(summary).build();
    }

    @Override
    public List<EntitySummary> getDescendants(String application, String entity, String typeRegex) {
        return EntityTransformer.entitySummaries(brooklyn().descendantsOfType(application, entity, typeRegex));
    }

    @Override
    public Map<String, Object> getDescendantsSensor(String application, String entity, String sensor, String typeRegex) {
        Iterable<Entity> descs = brooklyn().descendantsOfType(application, entity, typeRegex);
        return ApplicationResource.getSensorMap(sensor, descs);
    }

    @Override
    public List<LocationSummary> getLocations(String application, String entity) {
        List<LocationSummary> result = Lists.newArrayList();
        EntityLocal e = brooklyn().getEntity(application, entity);
        for (Location l : e.getLocations()) {
            result.add(LocationTransformer.newInstance(mgmt(), l, LocationDetailLevel.NONE));
        }
        return result;
    }

    @Override
    public String getSpec(String applicationToken, String entityToken) {
        EntityLocal entity = brooklyn().getEntity(applicationToken, entityToken);
        NamedStringTag spec = BrooklynTags.findFirst(BrooklynTags.YAML_SPEC_KIND, entity.tags().getTags());
        if (spec == null)
            return null;
        return (String) WebResourceUtils.getValueForDisplay(spec.getContents(), true, true);
    }
}
