package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;

import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/v1/locations")
@Apidoc("Locations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface LocationApi {

  @GET
  @ApiOperation(value = "Fetch the list of locations",
      responseClass = "brooklyn.rest.domain.LocationSummary",
      multiValueResponse = true)
  public List<LocationSummary> list() ;


  // this is here to support the web GUI's circles
  @GET
  @Path("/usage/LocatedLocations")
  @ApiOperation(value = "Return a summary of all usage", notes="interim API, expected to change")
  public Map<String,Map<String,Object>> getLocatedLocations() ;

  @GET
  @Path("/{locationId}")
  @ApiOperation(value = "Fetch details about a location",
      responseClass = "brooklyn.rest.domain.LocationSummary",
      multiValueResponse = true)
  public LocationSummary get(
          @ApiParam(value = "Location id to fetch", required = true)
          @PathParam("locationId") String locationId,
          @ApiParam(value = "Whether full (inherited) config should be compiled", required = false)
          @DefaultValue("false")
          @QueryParam("full") String fullConfig) ;
  
  /** @deprecated since 0.7.0; REST call now handled by above (optional query parameter added) */
  @Deprecated
  public LocationSummary get(String locationId);

  @POST
  @ApiOperation(value = "Create a new location", responseClass = "String")
  public Response create(
          @ApiParam(name = "locationSpec", value = "Location specification object", required = true)
          @Valid LocationSpec locationSpec) ;

  @DELETE
  @Path("/{locationId}")
  @ApiOperation(value = "Delete a location object by id")
  public void delete(
      @ApiParam(value = "Location id to delete", required = true)
      @PathParam("locationId") String locationId) ;

}
