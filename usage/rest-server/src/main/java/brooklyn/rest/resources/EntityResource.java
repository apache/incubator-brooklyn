package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.transform;

import java.net.URI;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.Task;
import brooklyn.rest.api.EntityApi;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.ResourceUtils;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class EntityResource extends AbstractBrooklynRestResource implements EntityApi {


   @Override
  public List<EntitySummary> list(final String application) {
    return summaryForChildrenEntities(brooklyn().getApplication(application));
  }

    @Override
  public EntitySummary get(String application, String entity) {
    return EntityTransformer.entitySummary(brooklyn().getEntity(application, entity));
  }

  @Override
  public Iterable<EntitySummary> getChildren( final String application, final String entity) {
    return summaryForChildrenEntities(brooklyn().getEntity(application, entity));
  }

  private List<EntitySummary> summaryForChildrenEntities(Entity rootEntity) {
    return Lists.newArrayList(transform(
        rootEntity.getChildren(),
        new Function<Entity, EntitySummary>() {
          @Override
          public EntitySummary apply(Entity entity) {
            return EntityTransformer.entitySummary(entity);
          }
        }));
  }
  
  @Override
  public Iterable<TaskSummary> listTasks(String applicationId, String entityId) {
      Entity entity = brooklyn().getEntity(applicationId, entityId);
      Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(mgmt().getExecutionManager(), entity);
      return Collections2.transform(tasks, TaskTransformer.FROM_TASK);
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
          Object content = new ResourceUtils(brooklyn().getCatalog().getRootClassLoader()).getResourceFromUrl(url);
          return Response.ok(content, mime).build();
      }
      
      // for anything else we do a redirect (e.g. http / https; perhaps ftp)
      return Response.temporaryRedirect(URI.create(url)).build();
  }

}
