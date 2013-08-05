package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.api.EntityApi;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.TaskSummary;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.Iterables.transform;

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
  public Iterable<TaskSummary> list(String applicationId, String entityId) {
      Entity entity = brooklyn().getEntity(applicationId, entityId);
      Set<Task<?>> tasks = mgmt().getExecutionManager().getTasksWithTag(entity);
      return Collections2.transform(tasks, TaskTransformer.FROM_TASK);
  }

  @Override
  public TaskSummary getTask(final String application, final String entityToken, String taskId) {
      // deprecate in favour of ActivityApi.get ?
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      return TaskTransformer.FROM_TASK.apply(t);
  }

}
