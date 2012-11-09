package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import brooklyn.util.exceptions.Exceptions;
import static com.google.common.base.Preconditions.checkNotNull;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

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

@Path("/v1/applications")
@Api(value = "/v1/applications", description = "Manage applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource extends BaseResource {

  private final ApplicationManager manager;
  private final CatalogResource catalog;
  private final LocationStore locations;
  private final ObjectMapper mapper = new ObjectMapper();

  public ApplicationResource(
      ApplicationManager manager, LocationStore locations, CatalogResource catalog
  ) {
    this.manager = checkNotNull(manager, "manager");
    this.locations = checkNotNull(locations, "locations");
    this.catalog = checkNotNull(catalog, "catalog");
  }

  @GET
  @Path("/tree")
  @ApiOperation(
      value = "Fetch applications and entities tree hierarchy"
  )
  public JsonNode applicationTree() {
    ArrayNode apps = mapper.createArrayNode();
    for (Application application : manager.registryById().values()) {
      apps.add(recursiveTreeFromEntity(application.getInstance()));
    }
    return apps;
  }

  public JsonNode recursiveTreeFromEntity(Entity entity) {
    ObjectNode aRoot = mapper.createObjectNode();
    aRoot.put("name", entity.getDisplayName());
    aRoot.put("id", entity.getId());
    aRoot.put("type", entity.getEntityType().getName());
    if (entity.getOwnedChildren().size() != 0) {
      aRoot.put("children", childEntitiesAsArray(entity));
    }
    return aRoot;
  }


  private ArrayNode childEntitiesAsArray(Entity entity) {
    ArrayNode node = mapper.createArrayNode();
    for (Entity e : entity.getOwnedChildren()) {
      node.add(recursiveTreeFromEntity(e));
    }
    return node;
  }

  @GET
  @ApiOperation(
      value = "Fetch list of applications",
      responseClass = "brooklyn.rest.api.Application"
  )
  public Iterable<Application> list() {
    return manager.registryById().values();
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
          value = "ID or name of application that needs to be fetched",
          required = true)
      @PathParam("application") String name) {
    Application app = manager.getApp(name);
    if (app!=null) return app;
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
          name = "applicationSpec",
          value = "Specification for application to be created, with name, locations, and entities or type fields",
          required = true)
      @Valid ApplicationSpec applicationSpec) {
      try {
    checkApplicationTypesAreValid(applicationSpec);
    checkLocationsAreValid(applicationSpec);

    if (manager.getApp(applicationSpec.getName())!=null) {
      throw preconditionFailed("Application '%s' already registered.", applicationSpec.getName());
    }
    Application app = manager.startInBackground(applicationSpec);

    URI ref = URI.create(applicationSpec.getName());
    return created(ref).entity(app.getId()).build();
      } catch (Exception e) {
          e.printStackTrace();
          throw Exceptions.propagate(e);
      }
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
          name = "application",
          value = "Application name",
          required = true
      )
      @PathParam("application") String application) {
    Application app = manager.getApp(application);
    if (app==null)
      throw notFound("Application '%s' not found.", application);

    manager.destroyAppByIdInBackground(app.getInstance().getId());
    return status(ACCEPTED).build();
  }

  private void checkApplicationTypesAreValid(ApplicationSpec applicationSpec) {
    if (applicationSpec.getType()!=null) {
        if (!catalog.containsEntity(applicationSpec.getType())) {
            throw notFound("Undefined application type '%s'", applicationSpec.getType());
        }
        if (applicationSpec.getEntities()!=null) {
            throw preconditionFailed("Application given explicit type '%s' must not define entities", applicationSpec.getType());
        }
        return;
    }
    for (EntitySpec entitySpec : applicationSpec.getEntities()) {
      if (!catalog.containsEntity(entitySpec.getType())) {
        throw notFound("Undefined entity type '%s'", entitySpec.getType());
      }
    }
  }

  private void checkLocationsAreValid(ApplicationSpec applicationSpec) {
    for (String locationRef : applicationSpec.getLocations()) {
      if (locations.getByRef(locationRef) == null) {
        throw notFound("Undefined location '%s'", locationRef);
      }
    }
  }
}
