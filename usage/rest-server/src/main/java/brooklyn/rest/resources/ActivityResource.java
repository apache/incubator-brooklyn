package brooklyn.rest.resources;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.BrooklynTaskTags.WrappedStream;
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
  public List<TaskSummary> children(String taskId) {
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      if (!(t instanceof HasTaskChildren))
          return Collections.emptyList();
      return new LinkedList<TaskSummary>(Collections2.transform(Lists.newArrayList(((HasTaskChildren)t).getChildren()), 
              TaskTransformer.FROM_TASK));
  }

  public String stream(String taskId, String streamId) {
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      WrappedStream stream = BrooklynTaskTags.stream(t, streamId);
      if (stream==null)
          throw WebResourceUtils.notFound("Cannot find stream '%s' in task '%s'", streamId, taskId);
      return stream.streamContents.get();
  }
  
}
