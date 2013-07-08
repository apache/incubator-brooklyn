package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.api.ActivityApi;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.util.WebResourceUtils;
import com.google.common.collect.Collections2;

import java.util.Set;

public class ActivityResource extends AbstractBrooklynRestResource implements ActivityApi {

    @Override
    public Iterable<TaskSummary> list(String applicationId, String entityId) {
      Entity entity = brooklyn().getEntity(applicationId, entityId);
      Set<Task<?>> tasks = mgmt().getExecutionManager().getTasksWithTag(entity);
      return Collections2.transform(tasks, TaskTransformer.FROM_TASK);
  }

  @Override
  public TaskSummary get( final String application, final String entityToken, String taskId
  ) {
//      final EntityLocal entity = brooklyn().getEntity(application, entityToken);
      // no entity checking done/needed
      // (should API be refactored to be a top-level?)
      
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      return TaskTransformer.FROM_TASK.apply(t);
  }

}
