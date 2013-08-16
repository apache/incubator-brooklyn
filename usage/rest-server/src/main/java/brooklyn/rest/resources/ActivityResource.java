package brooklyn.rest.resources;

import java.util.Collections;

import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.rest.api.ActivityApi;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.WebResourceUtils;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

public class ActivityResource extends AbstractBrooklynRestResource implements ActivityApi {

  @Override
  public TaskSummary get(String taskId) {
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      return TaskTransformer.FROM_TASK.apply(t);
  }

  @Override
  public Iterable<TaskSummary> children(String taskId) {
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      if (!(t instanceof HasTaskChildren))
          return Collections.emptyList();
      return Collections2.transform(Lists.newArrayList(((HasTaskChildren)t).getChildren()), 
              TaskTransformer.FROM_TASK);
  }

}
