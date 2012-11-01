package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.TaskSummary;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Collections2;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Set;

@Path("/v1/applications/{application}/entities/{entity}/activities")
@Api(value = "/v1/applications/{application}/activities", description = "Inspect applications activity")
@Produces(MediaType.APPLICATION_JSON)
public class ActivityResource extends BaseResource {

  private final ApplicationManager manager;

  public ActivityResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "application manager");
  }

  @GET
  @ApiOperation(
      value = "Fetch list of activities for this application"
  )
  public Iterable<TaskSummary> list(@PathParam("application") String name,
                                    @PathParam("entity") String entityIdOrName) {
    Application application = manager.getApp(name);
    if (application!=null) {
      Entity entity = getEntityOr404(application, entityIdOrName);
      Set<Task<?>> tasks = application.getInstance().getManagementContext().getExecutionManager().getTasksWithTag(entity);
      return Collections2.transform(tasks, new Function<Task<?>, TaskSummary>() {
        @Override
        public TaskSummary apply(@Nullable Task<?> input) {
          return new TaskSummary(input);
        }
      });
    }
    throw notFound("Application '%s' not found.", name);
  }
}
