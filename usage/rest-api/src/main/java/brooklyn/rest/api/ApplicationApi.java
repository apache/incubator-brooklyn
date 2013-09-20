package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;
import org.codehaus.jackson.JsonNode;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/applications")
@Apidoc("Applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ApplicationApi {

  @GET
  @Path("/tree")
  @ApiOperation(
      value = "Fetch applications and entities tree hierarchy"
  )
  /** @deprecated since 0.6.0 use {@link #fetch(String)} (with slightly different, but better semantics) */
  @Deprecated
  public JsonNode applicationTree();
   
  @GET
  @Path("/fetch")
  @ApiOperation(
      value = "Fetch display details for all applications and optionally selected additional entities"
  )
  public JsonNode fetch(
          @ApiParam(value="Selected additional entity ID's to include, comma-separated", required=false)
          @DefaultValue("")
          @QueryParam("items") String items);

  @GET
  @ApiOperation(
      value = "Fetch list of applications, as ApplicationSummary objects",
      responseClass = "brooklyn.rest.domain.ApplicationSummary"
  )
  public Iterable<ApplicationSummary> list() ;

  @GET
  @Path("/{application}")
  @ApiOperation(
      value = "Fetch a specific application",
      responseClass = "brooklyn.rest.domain.ApplicationSummary"
  )
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application not found")
  })
  public ApplicationSummary get(
          @ApiParam(
              value = "ID or name of application whose details will be returned",
              required = true)
          @PathParam("application") String application) ;

  @POST
  @ApiOperation(
      value = "Create and start a new application",
      responseClass = "brooklyn.rest.domain.TaskSummary"
  )
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Undefined entity or location"),
      @ApiError(code = 412, reason = "Application already registered")
  })
  public Response create(
          @ApiParam(
              name = "applicationSpec",
              value = "Specification for application to be created, with name, locations, and entities or type fields",
              required = true)
          @Valid ApplicationSpec applicationSpec) ;

  @DELETE
  @Path("/{application}")
  @ApiOperation(
      value = "Delete a specified application",
      responseClass = "brooklyn.rest.domain.TaskSummary"
  )
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application not found")
  })
  public Response delete(
          @ApiParam(
              name = "application",
              value = "Application name",
              required = true
          )
          @PathParam("application") String application) ;

}
