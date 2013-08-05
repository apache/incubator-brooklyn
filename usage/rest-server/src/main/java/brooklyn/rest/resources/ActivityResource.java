package brooklyn.rest.resources;

import brooklyn.management.Task;
import brooklyn.rest.api.ActivityApi;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.WebResourceUtils;

public class ActivityResource extends AbstractBrooklynRestResource implements ActivityApi {

  @Override
  public TaskSummary get(String taskId) {
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      return TaskTransformer.FROM_TASK.apply(t);
  }

}
