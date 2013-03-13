package brooklyn.rest.resources;

import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.util.WebResourceUtils;

import com.google.common.collect.Collections2;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/activities")
@Apidoc("Activities")
@Produces(MediaType.APPLICATION_JSON)
public class ActivityResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(
      value = "Fetch list of activities for this application"
  )
  public Iterable<TaskSummary> list(@PathParam("application") String applicationId,
                                    @PathParam("entity") String entityId) {
      Entity entity = brooklyn().getEntity(applicationId, entityId);
      Set<Task<?>> tasks = mgmt().getExecutionManager().getTasksWithTag(entity);
      return Collections2.transform(tasks, TaskSummary.FROM_TASK);
  }

  @GET
  @Path("/{task}")
  @ApiOperation(value = "Fetch task details", responseClass = "brooklyn.rest.domain.TaskSummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or task")
  })
  @Produces("text/json")
  public TaskSummary get(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken,
          @ApiParam(value = "Task ID", required = true)
          @PathParam("task") String taskId
  ) {
//      final EntityLocal entity = brooklyn().getEntity(application, entityToken);
      // no entity checking done/needed
      // (should API be refactored to be a top-level?)
      
      Task<?> t = mgmt().getExecutionManager().getTask(taskId);
      if (t==null)
          throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
      return TaskSummary.FROM_TASK.apply(t);
  }

}
