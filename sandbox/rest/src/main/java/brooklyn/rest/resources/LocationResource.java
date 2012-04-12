package brooklyn.rest.resources;

import brooklyn.rest.api.Location;
import brooklyn.rest.core.LocationStore;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/locations")
@Produces(MediaType.APPLICATION_JSON)
public class LocationResource {

  private final LocationStore store;

  public LocationResource(LocationStore store) {
    this.store = store;
  }

  @GET
  public Iterable<Map<String, String>> listLocations() {
    return Iterables.transform(store.entries(),
        new Function<Map.Entry<Integer, Location>, Map<String, String>>() {
          @Override
          public Map<String, String> apply(Map.Entry<Integer, Location> entry) {
            return asMap(entry.getKey(), entry.getValue());
          }
        });
  }

  @GET
  @Path("{location}")
  public Map<String, String> getLocation(@PathParam("location") Integer id) {
    return asMap(id, store.get(id));
  }

  private Map<String, String> asMap(Integer id, Location loc) {
    return ImmutableMap.of(
        "ref", "/locations/" + id,
        "provider", loc.getProvider(),
        "identity", loc.getIdentity(),
        "location", loc.getLocation()
    );
  }

  @POST
  public void add(@Valid Location location) {
    store.put(location);
  }

  @DELETE
  @Path("{location}")
  public void delete(@PathParam("location") Integer locationId) {
    store.remove(locationId);
  }

}
