package brooklyn.rest.resources;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import com.wordnik.swagger.core.*;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;

@Path("/v1/applications")
@Api(value = "/v1/applications", description = "Manage applications")
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
  @ApiOperation(
    value = "Fetch list of applications",
    responseClass = "brooklyn.rest.api.Application"
  )
  public Iterable<Application> list() {
    return manager.registry().values();
  }

  @GET
  @Path("/{application}")
  @ApiOperation(
    value = "Fetch a specific application",
    responseClass = "brooklyn.rest.api.Application"
  )
  @ApiErrors(value = {
    @ApiError(code = 404, reason = "Application not found")
  })
  public Application get(
    @ApiParam(
      value = "Name of application that needs to be fetched",
      required = true)
    @PathParam("application") String name) {
    if (manager.registry().containsKey(name)) {
      return manager.registry().get(name);
    }
    throw notFound("Application '%s' not found.", name);
  }

  @POST
  @ApiOperation(
    value = "Create a new application"
  )
  @ApiErrors(value = {
    @ApiError(code = 404, reason = "Undefined entity or location"),
    @ApiError(code = 412, reason = "Application already registered")
  })
  public Response create(
    @ApiParam(
      value = "Specification for application to be created",
      required = true)
    @Valid ApplicationSpec applicationSpec) {
    checkAllEntityTypesAreValid(applicationSpec);
    checkAllLocationsAreValid(applicationSpec);

    if (manager.registry().containsKey(applicationSpec.getName())) {
      throw preconditionFailed("Application '%s' already registered.",
        applicationSpec.getName());
    }
    manager.startInBackground(applicationSpec);

    URI ref = URI.create(applicationSpec.getName());
    return created(ref).build();
  }


  @DELETE
  @Path("/{application}")
  @ApiOperation(
    value = "Delete a specified application"
  )
  @ApiErrors(value = {
    @ApiError(code = 404, reason = "Application not found")
  })
  public Response delete(
    @ApiParam(
      value = "Application name",
      required = true
    )
    @PathParam("application") String application) {
    if (!manager.registry().containsKey(application))
      throw notFound("Application '%s' not found.", application);

    manager.destroyInBackground(application);
    return status(ACCEPTED).build();
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
