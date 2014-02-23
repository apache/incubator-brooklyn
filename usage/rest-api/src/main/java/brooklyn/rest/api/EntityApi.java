package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.domain.TaskSummary;

import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities")
@Apidoc("Entities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EntityApi {

  @GET
  @ApiOperation(value = "Fetch the list of entities for a given application",
      responseClass = "brooklyn.rest.domain.EntitySummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application not found")
  })
  public List<EntitySummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application) ;

  @GET
  @Path("/{entity}")
  @ApiOperation(value = "Fetch details about a specific application entity",
      responseClass = "brooklyn.rest.domain.EntitySummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity missing")
  })
  public EntitySummary get(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entity
  ) ;

  // TODO rename as "/children" ?
  @GET
  @ApiOperation(value = "Fetch details about a specific application entity's children",
          responseClass = "brooklyn.rest.domain.EntitySummary")
  @Path("/{entity}/entities")
  public Iterable<EntitySummary> getChildren(
      @PathParam("application") final String application,
      @PathParam("entity") final String entity
  ) ;

  @GET
  @Path("/{entity}/activities")
  @ApiOperation(value = "Fetch list of tasks for this entity")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public Iterable<TaskSummary> listTasks(
          @ApiParam(value = "Entity ID or name", required = true) @PathParam("application") String applicationId,
          @ApiParam(value = "Application ID or name", required = true) @PathParam("entity") String entityId);

  @GET
  @Path("/{entity}/activities/{task}")
  @ApiOperation(value = "Fetch task details", responseClass = "brooklyn.rest.domain.TaskSummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or task")
  })
  @Produces("text/json")
  public TaskSummary getTask(
          @ApiParam(value = "Application ID or name", required = true) @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true) @PathParam("entity") final String entityToken,
          @ApiParam(value = "Task ID", required = true) @PathParam("task") String taskId
  );

  @GET
  @ApiOperation(value = "Returns an icon for the entity, if defined")
  @Path("/{entity}/icon")
  public Response getIcon(
          @PathParam("application") final String application,
          @PathParam("entity") final String entity
  );
  
  @POST
  @ApiOperation(
      value = "Expunge an entity",
      responseClass = "brooklyn.rest.domain.TaskSummary"
  )
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Undefined application or entity")
  })
  @Path("/{entity}/expunge")
  public Response expunge(@PathParam("application") final String application, @PathParam("entity") final String entity, @QueryParam("release") final boolean release);
  
  @GET
  @Path("/{entity}/descendants")
  @ApiOperation(value = "Fetch entity info for all (or filtered) descendants",
      responseClass = "brooklyn.rest.domain.EntitySummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity missing")
  })
  public Iterable<EntitySummary> getDescendants(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entity,
      @ApiParam(value="Regular expression for an entity type which must be matched", required=false)
      @DefaultValue(".*")
      @QueryParam("typeRegex") String typeRegex
  );

  @GET
  @Path("/{entity}/descendants/sensor/{sensor}")
  @ApiOperation(value = "Fetch values of a given sensor for all (or filtered) descendants")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity missing")
  })
  public Map<String,Object> getDescendantsSensor(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entity,
      @ApiParam(value = "Sensor name", required = true)
      @PathParam("sensor") String sensor,
      @ApiParam(value="Regular expression for an entity type which must be matched", required=false)
      @DefaultValue(".*")
      @QueryParam("typeRegex") String typeRegex
  );

  @GET
  @Path("/{entity}/locations")
  @ApiOperation(value = "List the locations set on the entity")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application or entity missing")
  })
  public Iterable<LocationSummary> getLocations(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entity);
  
}
