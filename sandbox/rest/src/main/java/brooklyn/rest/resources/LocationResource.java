package brooklyn.rest.resources;

import brooklyn.rest.api.LocationSpec;
import brooklyn.rest.api.LocationSummary;
import brooklyn.rest.core.LocationStore;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Lists;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

@Path("/v1/locations")
@Api(value = "/v1/locations", description = "Manage locations")
@Produces(MediaType.APPLICATION_JSON)
public class LocationResource extends BaseResource {

  private final LocationStore store;

  public LocationResource(LocationStore store) {
    this.store = checkNotNull(store, "store");
  }

  @GET
  @ApiOperation(value = "Fetch the list of locations",
      responseClass = "brooklyn.rest.api.LocationSummary",
      multiValueResponse = true)
  public List<LocationSummary> list() {
    return Lists.newArrayList(transform(store.entries(),
        new Function<Map.Entry<Integer, LocationSpec>, LocationSummary>() {
          @Override
          public LocationSummary apply(Map.Entry<Integer, LocationSpec> entry) {
            return new LocationSummary(entry.getKey().toString(), entry.getValue());
          }
        }));
  }

  @GET
  @Path("/{location}")
  @ApiOperation(value = "Fetch details about a location",
      responseClass = "brooklyn.rest.api.LocationSummary",
      multiValueResponse = true)
  public LocationSummary get(
      @ApiParam(value = "Location id to fetch", required = true)
      @PathParam("location") Integer locationId) {
    return new LocationSummary(locationId.toString(), store.get(locationId));
  }

  @POST
  @ApiOperation(value = "Create a new location", responseClass = "String")
  public Response create(
      @ApiParam(name = "locationSpec", value = "Location specification object", required = true)
      @Valid LocationSpec locationSpec) {
    int id = store.put(locationSpec);
    return Response.created(URI.create("" + id)).build();
  }

  @DELETE
  @Path("/{location}")
  @ApiOperation(value = "Delete a location object by id")
  public void delete(
      @ApiParam(value = "Location id to delete", required = true)
      @PathParam("location") Integer locationId) {
    store.remove(locationId);
  }

}
