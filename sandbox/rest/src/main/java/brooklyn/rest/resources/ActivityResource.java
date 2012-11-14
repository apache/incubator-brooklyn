package brooklyn.rest.resources;

import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.domain.TaskSummary;

import com.google.common.collect.Collections2;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;

@Path("/v1/applications/{application}/entities/{entity}/activities")
@Api(value = "/v1/applications/{application}/activities", description = "Inspect applications activity")
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
  
}
