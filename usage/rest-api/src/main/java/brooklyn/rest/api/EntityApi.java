package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EntitySummary;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/v1/applications/{application}/entities")
@Apidoc("Application entities")
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

}
