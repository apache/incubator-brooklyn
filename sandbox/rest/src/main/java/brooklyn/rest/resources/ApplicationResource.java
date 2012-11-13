package brooklyn.rest.resources;

import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.ACCEPTED;

import java.net.URI;

import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.ApplicationSummary;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.api.TaskSummary;
import brooklyn.rest.core.WebResourceUtils;

import com.google.common.collect.Collections2;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications")
@Api(value = "/v1/applications", description = "Manage applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource extends BrooklynResourceBase {

//  private final ApplicationManager manager;
//  private final CatalogResource catalog;
//  private final LocationStore locations;
  private final ObjectMapper mapper = new ObjectMapper();

//  public ApplicationResource(
//      ApplicationManager manager, LocationStore locations, CatalogResource catalog
//  ) {
//    this.manager = checkNotNull(manager, "manager");
//    this.locations = checkNotNull(locations, "locations");
//    this.catalog = checkNotNull(catalog, "catalog");
//  }

  @GET
  @Path("/tree")
  @ApiOperation(
      value = "Fetch applications and entities tree hierarchy"
  )
  public JsonNode applicationTree() {
    ArrayNode apps = mapper.createArrayNode();
    for (Application application : mgmt().getApplications())
      apps.add(recursiveTreeFromEntity(application));
    return apps;
  }
  private JsonNode recursiveTreeFromEntity(Entity entity) {
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
      responseClass = "brooklyn.rest.api.ApplicationSummary"
  )
  public Iterable<ApplicationSummary> list() {
    return Collections2.transform(mgmt().getApplications(), ApplicationSummary.FROM_APPLICATION);
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
  public ApplicationSummary get(
          @ApiParam(
              value = "ID or name of application whose details will be returned",
              required = true)
          @PathParam("application") String application) {
      return ApplicationSummary.fromApplication(brooklyn().getApplication(application));
  }

  @POST
  @ApiOperation(
      value = "Create and start a new application",
      responseClass = "brooklyn.rest.resources.TaskSummary"
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
      checkApplicationTypesAreValid(applicationSpec);
      checkLocationsAreValid(applicationSpec);
      // TODO duplicate prevention
      Application app = brooklyn().create(applicationSpec);
      Task<?> t = brooklyn().start(app, applicationSpec);
      TaskSummary ts = TaskSummary.FROM_TASK.apply(t);
      URI ref = URI.create(app.getApplicationId());
      return created(ref).entity(ts).build();
  }

  @DELETE
  @Path("/{application}")
  @ApiOperation(
      value = "Delete a specified application",
      responseClass = "brooklyn.rest.resources.TaskSummary"
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
      Task<?> t = brooklyn().destroy(brooklyn().getApplication(application));
      TaskSummary ts = TaskSummary.FROM_TASK.apply(t);
      return status(ACCEPTED).entity(ts).build();
  }

  private void checkApplicationTypesAreValid(ApplicationSpec applicationSpec) {
    if (applicationSpec.getType()!=null) {
        if (!brooklyn().getCatalog().containsEntity(applicationSpec.getType())) {
            throw WebResourceUtils.notFound("Undefined application type '%s'", applicationSpec.getType());
        }
        if (applicationSpec.getEntities()!=null) {
            throw WebResourceUtils.preconditionFailed("Application given explicit type '%s' must not define entities", applicationSpec.getType());
        }
        return;
    }
    for (EntitySpec entitySpec : applicationSpec.getEntities()) {
      if (!brooklyn().getCatalog().containsEntity(entitySpec.getType())) {
        throw WebResourceUtils.notFound("Undefined entity type '%s'", entitySpec.getType());
      }
    }
  }
  private void checkLocationsAreValid(ApplicationSpec applicationSpec) {
    for (String locationRef : applicationSpec.getLocations()) {
      if (brooklyn().getLocationStore().getByRef(locationRef) == null) {
        throw WebResourceUtils.notFound("Undefined location '%s'", locationRef);
      }
    }
  }
}
