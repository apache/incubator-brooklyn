package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EffectorSummary;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities/{entity}/effectors")
@Apidoc("Entity effectors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EffectorApi {

  @GET
  @ApiOperation(value = "Fetch the list of effectors",
      responseClass = "brooklyn.rest.domain.EffectorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<EffectorSummary> list(
      @ApiParam(name = "application", value = "Application name", required = true)
      @PathParam("application") final String application,
      @ApiParam(name = "entity", value = "Entity name", required = true)
      @PathParam("entity") final String entityToken
  ) ;

  @POST
  @Path("/{effector}")
  @ApiOperation(value = "Trigger an effector",
    notes="Returns the return value (status 200) if it completes, or an activity task ID (status 202) if it times out")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or effector")
  })
  public Response invoke(
      @ApiParam(name = "application", value = "Application ID or name", required = true)
      @PathParam("application") String application,
      
      @ApiParam(name = "entity", value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      
      @ApiParam(name = "effector", value = "Name of the effector to trigger", required = true)
      @PathParam("effector") String effectorName,
      
      // TODO test timeout; and should it be header, form, or what?
      @ApiParam(name = "timeout", value = "Delay before server should respond with activity task ID rather than result (in millis if no unit specified): " +
      		"'never' (blocking) is default; " +
      		"'0' means 'always' return task activity ID; " +
      		"and e.g. '1000' or '1s' will return a result if available within one second otherwise status 202 and the activity task ID", 
      		required = false, defaultValue = "never")
      @QueryParam("timeout")
      String timeout,
      
      @ApiParam(name = "parameters", value = "Effector parameters (as key value pairs)", required = false)
      @Valid 
      Map<String, String> parameters
  ) ;
  
}
