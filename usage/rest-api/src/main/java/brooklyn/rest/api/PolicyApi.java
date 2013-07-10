package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.PolicySummary;
import brooklyn.rest.domain.Status;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities/{entity}/policies")
@Apidoc("Entity policies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PolicyApi {
    
  @GET
  @ApiOperation(value = "Fetch the policies attached to a specific application entity",
      responseClass = "brooklyn.rest.domain.PolicySummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<PolicySummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken
  ) ;

  // TODO support parameters  ?show=value,summary&name=xxx
  // (and in sensors class)
  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch policy states in batch", notes="Returns a map of policy ID to whether it is active")
  public Map<String, Boolean> batchConfigRead(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken) ;
  
  @POST
  @ApiOperation(value = "Add a policy", notes="Returns ID of policy added; policy type must have no-arg constructor " +
  		"and setConfig(Map) method should be available if non-empty config is supplied")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public String addPolicy(
      @ApiParam(name = "application", value = "Application ID or name", required = true)
      @PathParam("application") String application,
      
      @ApiParam(name = "entity", value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      
      @ApiParam(name = "policyType", value = "Class of policy to add", required = true)
      @QueryParam("type")
      String policyTypeName,
      
      // TODO would like to make this optional but jersey complains if we do
      @ApiParam(name = "config", value = "Configuration for the policy (as key value pairs)", required = true)
      Map<String, String> config
  ) ;
  
  @GET
  @Path("/{policy}")
  @ApiOperation(value = "Gets status of a policy (RUNNING / SUSPENDED)")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Status getStatus(
      @ApiParam(name = "application", value = "Application ID or name", required = true)
      @PathParam("application") String application,
      
      @ApiParam(name = "entity", value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      
      @ApiParam(name = "policy", value = "Policy ID or name", required = true)
      @PathParam("policy") String policyId
  ) ;

  @POST
  @Path("/{policy}/start")
  @ApiOperation(value = "Start or resume a policy")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Response start(
          @ApiParam(name = "application", value = "Application ID or name", required = true)
          @PathParam("application") String application,
          
          @ApiParam(name = "entity", value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          
          @ApiParam(name = "policy", value = "Policy ID or name", required = true)
          @PathParam("policy") String policyId
  ) ;

  @POST
  @Path("/{policy}/stop")
  @ApiOperation(value = "Suspends a policy")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Response stop(
          @ApiParam(name = "application", value = "Application ID or name", required = true)
          @PathParam("application") String application,
          
          @ApiParam(name = "entity", value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          
          @ApiParam(name = "policy", value = "Policy ID or name", required = true)
          @PathParam("policy") String policyId
  ) ;

  @POST
  @Path("/{policy}/destroy")
  @ApiOperation(value = "Destroy a policy", notes="Removes a policy from being associated with the entity and destroys it (stopping first if running)")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Response destroy(
          @ApiParam(name = "application", value = "Application ID or name", required = true)
          @PathParam("application") String application,
          
          @ApiParam(name = "entity", value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          
          @ApiParam(name = "policy", value = "Policy ID or name", required = true)
          @PathParam("policy") String policyToken
  ) ;
}
