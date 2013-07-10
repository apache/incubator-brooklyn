package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.TaskSummary;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/v1/applications/{application}/entities/{entity}/activities")
@Apidoc("Activities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ActivityApi {

  @GET
  @ApiOperation(
      value = "Fetch list of activities for this application"
  )
  public Iterable<TaskSummary> list(@PathParam("application") String applicationId,
                                    @PathParam("entity") String entityId);

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
  );

}
