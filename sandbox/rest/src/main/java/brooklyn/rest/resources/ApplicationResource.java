package brooklyn.rest.resources;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import static com.google.common.base.Preconditions.checkNotNull;
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
    throw notFound("Application '%s' not found.", name);
  }

  @POST
  public Response create(@Valid ApplicationSpec applicationSpec) {
    checkAllEntityTypesAreValid(applicationSpec);
    checkAllLocationsAreValid(applicationSpec);

    if (manager.registry().containsKey(applicationSpec.getName())) {
      throw preconditionFailed("Application '%s' already registered.",
          applicationSpec.getName());
    }
    manager.startInBackground(applicationSpec);

    URI ref = URI.create(applicationSpec.getName());
    return Response.created(ref).build();
  }


  @DELETE
  @Path("{application}")
  public Response delete(@PathParam("application") String application) {
    if (!manager.registry().containsKey(application))
      throw notFound("Application '%s' not found.", application);

    manager.destroyInBackground(application);
    return Response.status(Response.Status.ACCEPTED).build();
  }

  private void checkAllEntityTypesAreValid(ApplicationSpec applicationSpec) {
    for (EntitySpec entitySpec : applicationSpec.getEntities()) {
      if (!catalog.containsEntity(entitySpec.getType())) {
        throw notFound("Undefined entity type '%s'", entitySpec.getType());
      }
    }
  }

  private void checkAllLocationsAreValid(ApplicationSpec applicationSpec) {
    for (String locationRef : applicationSpec.getLocations()) {
      if (locations.getByRef(locationRef) == null) {
        throw notFound("Undefined location '%s'", locationRef);
      }
    }
  }
}
