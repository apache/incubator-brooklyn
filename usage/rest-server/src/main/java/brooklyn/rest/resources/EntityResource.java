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

import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.ACCEPTED;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.management.entitlement.EntitlementPredicates;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.rest.api.EntityApi;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.transform.LocationTransformer;
import brooklyn.rest.transform.LocationTransformer.LocationDetailLevel;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;

import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class EntityResource extends AbstractBrooklynRestResource implements EntityApi {

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
  public List<EntitySummary> getChildren( final String application, final String entity) {
      return FluentIterable
              .from(brooklyn().getEntity(application, entity).getChildren())
              .filter(EntitlementPredicates.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY))
              .transform(EntityTransformer.FROM_ENTITY)
              .toList();
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
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      return TaskTransformer.FROM_TASK.apply(t);
  }

  @Override
  public List<Object> listTags(String applicationId, String entityId) {
      Entity entity = brooklyn().getEntity(applicationId, entityId);
      return MutableList.copyOf(entity.getTagSupport().getTags());
  }

  @Override
  public Response getIcon(String applicationId, String entityId) {
      EntityLocal entity = brooklyn().getEntity(applicationId, entityId);
      String url = entity.getIconUrl();
      if (url==null)
          return Response.status(Status.NO_CONTENT).build();
      
      if (brooklyn().isUrlServerSideAndSafe(url)) {
          // classpath URL's we will serve IF they end with a recognised image format;
          // paths (ie non-protocol) and 
          // NB, for security, file URL's are NOT served
          MediaType mime = WebResourceUtils.getImageMediaTypeFromExtension(Files.getFileExtension(url));
          Object content = ResourceUtils.create(brooklyn().getCatalog().getRootClassLoader()).getResourceFromUrl(url);
          return Response.ok(content, mime).build();
      }
      
      // for anything else we do a redirect (e.g. http / https; perhaps ftp)
      return Response.temporaryRedirect(URI.create(url)).build();
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
      for (Location l: e.getLocations()) {
          result.add(LocationTransformer.newInstance(mgmt(), l, LocationDetailLevel.NONE));
      }
      return result;
  }

}
