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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.rest.util.WebResourceUtils;

import com.google.common.collect.Collections2;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications")
@Apidoc("Applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource extends AbstractBrooklynRestResource {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(ApplicationResource.class);
  
  private final ObjectMapper mapper = new ObjectMapper();

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
    if (entity.getChildren().size() != 0) {
      aRoot.put("children", childEntitiesAsArray(entity));
    }
    return aRoot;
  }
  private ArrayNode childEntitiesAsArray(Entity entity) {
    ArrayNode node = mapper.createArrayNode();
    for (Entity e : entity.getChildren()) {
      node.add(recursiveTreeFromEntity(e));
    }
    return node;
  }

  @GET
  @ApiOperation(
      value = "Fetch list of applications",
      responseClass = "brooklyn.rest.domain.ApplicationSummary"
  )
  public Iterable<ApplicationSummary> list() {
    return Collections2.transform(mgmt().getApplications(), ApplicationSummary.FROM_APPLICATION);
  }

  @GET
  @Path("/{application}")
  @ApiOperation(
      value = "Fetch a specific application",
      responseClass = "brooklyn.rest.domain.ApplicationSummary"
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
      responseClass = "brooklyn.rest.domain.TaskSummary"
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
      responseClass = "brooklyn.rest.domain.TaskSummary"
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
        if (brooklyn().getCatalog().getCatalogItem(applicationSpec.getType())==null) {
            throw WebResourceUtils.notFound("Undefined application type '%s'", applicationSpec.getType());
        }
        if (applicationSpec.getEntities()!=null) {
            throw WebResourceUtils.preconditionFailed("Application given explicit type '%s' must not define entities", applicationSpec.getType());
        }
        return;
    }
    for (EntitySpec entitySpec : applicationSpec.getEntities()) {
      if (brooklyn().getCatalog().getCatalogItem(entitySpec.getType())==null) {
        throw WebResourceUtils.notFound("Undefined entity type '%s'", entitySpec.getType());
      }
    }
  }
  @SuppressWarnings("deprecation")
  private void checkLocationsAreValid(ApplicationSpec applicationSpec) {
    for (String locationId : applicationSpec.getLocations()) {
        locationId = BrooklynRestResourceUtils.fixLocation(locationId);
      if (!brooklyn().getLocationRegistry().canResolve(locationId)) {
        throw WebResourceUtils.notFound("Undefined location '%s'", locationId);
      }
    }
  }
}
