package brooklyn.rest.resources;

import brooklyn.rest.api.LocationSpec;
import brooklyn.rest.api.LocationSummary;
import brooklyn.rest.core.LocationStore;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Iterables;
import java.net.URI;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/locations")
@Produces(MediaType.APPLICATION_JSON)
public class LocationResource {

  private final LocationStore store;

  public LocationResource(LocationStore store) {
    this.store = checkNotNull(store, "store");
  }

  @GET
  public Iterable<LocationSummary> list() {
    return Iterables.transform(store.entries(),
        new Function<Map.Entry<Integer, LocationSpec>, LocationSummary>() {
          @Override
          public LocationSummary apply(Map.Entry<Integer, LocationSpec> entry) {
            return new LocationSummary(entry.getKey().toString(), entry.getValue());
          }
        });
  }

  @GET
  @Path("{location}")
  public LocationSummary get(@PathParam("location") Integer locationId) {
    return new LocationSummary(locationId.toString(), store.get(locationId));
  }

  @POST
  public Response create(@Valid LocationSpec locationSpec) {
    int id = store.put(locationSpec);
    return Response.created(URI.create("" + id)).build();
  }

  @DELETE
  @Path("{location}")
  public void delete(@PathParam("location") Integer locationId) {
    store.remove(locationId);
  }

}
