package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.net.URI;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource extends BaseResource {

  private final ApplicationManager manager;
  private final CatalogResource catalog;
  private final LocationStore locations;

  public ApplicationResource(
      ApplicationManager manager, LocationStore locations, CatalogResource catalog
  ) {
    this.manager = checkNotNull(manager, "manager");
    this.locations = checkNotNull(locations, "locations");
    this.catalog = checkNotNull(catalog, "catalog");
  }

  @GET
  public Iterable<Application> list() {
    return manager.registry().values();
  }

  @GET
  @Path("{application}")
  public Application get(@PathParam("application") String name) {
    if (manager.registry().containsKey(name)) {
      return manager.registry().get(name);
    }
    throw new WebApplicationException(Response.Status.NOT_FOUND);
  }

  @POST
  public Response create(@Valid ApplicationSpec applicationSpec) {
    if (anyEntityIsNotAvailable(applicationSpec) ||
        anyLocationIsNotValid(applicationSpec)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    manager.startInBackground(applicationSpec);

    URI ref = URI.create(applicationSpec.getName());
    return Response.created(ref).build();
  }


  @DELETE
  @Path("{application}")
  public Response delete(@PathParam("application") String application) {
    manager.destroyInBackground(application);
    return Response.status(Response.Status.ACCEPTED).build();
  }

  private boolean anyEntityIsNotAvailable(ApplicationSpec applicationSpec) {
    for (EntitySpec entitySpec : applicationSpec.getEntities()) {
      if (!catalog.containsEntity(entitySpec.getType())) {
        return true;
      }
    }
    return false;
  }

  private boolean anyLocationIsNotValid(ApplicationSpec applicationSpec) {
    for (String locationRef : applicationSpec.getLocations()) {
      if (locations.getByRef(locationRef) == null) {
        return true;
      }
    }
    return false;
  }
}
