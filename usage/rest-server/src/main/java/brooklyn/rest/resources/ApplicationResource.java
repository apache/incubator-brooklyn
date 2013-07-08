package brooklyn.rest.resources;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.api.ApplicationApi;
import brooklyn.rest.domain.*;
import brooklyn.rest.transform.ApplicationTransformer;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.rest.util.WebResourceUtils;
import com.google.common.collect.Collections2;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;


public class ApplicationResource extends AbstractBrooklynRestResource implements ApplicationApi {

  private static final Logger log = LoggerFactory.getLogger(ApplicationResource.class);
  
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
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

  @Override
  public Iterable<ApplicationSummary> list() {
    return Collections2.transform(mgmt().getApplications(), ApplicationTransformer.FROM_APPLICATION);
  }

  @Override
  public ApplicationSummary get(String application) {
      return ApplicationTransformer.summaryFromApplication(brooklyn().getApplication(application));
  }

  @Override
  public Response create(ApplicationSpec applicationSpec) {
      checkApplicationTypesAreValid(applicationSpec);
      checkLocationsAreValid(applicationSpec);
      // TODO duplicate prevention
      Application app = brooklyn().create(applicationSpec);
      Task<?> t = brooklyn().start(app, applicationSpec);
      TaskSummary ts = TaskTransformer.FROM_TASK.apply(t);
      URI ref = URI.create(app.getApplicationId());
      return created(ref).entity(ts).build();
  }

  @Override
  public Response delete(String application) {
      Task<?> t = brooklyn().destroy(brooklyn().getApplication(application));
      TaskSummary ts = TaskTransformer.FROM_TASK.apply(t);
      return status(ACCEPTED).entity(ts).build();
  }

  private void checkApplicationTypesAreValid(ApplicationSpec applicationSpec) {
      String appType = applicationSpec.getType();
      if (appType != null) {
          checkEntityTypeIsValid(appType);

          if (applicationSpec.getEntities() != null) {
              throw WebResourceUtils.preconditionFailed("Application given explicit type '%s' must not define entities", appType);
          }
          return;
      }
        
      for (EntitySpec entitySpec : applicationSpec.getEntities()) {
          String entityType = entitySpec.getType();
          checkEntityTypeIsValid(checkNotNull(entityType, "entityType"));
      }
  }

    private void checkEntityTypeIsValid(String type) {
        if (brooklyn().getCatalog().getCatalogItem(type) == null) {
            try {
                brooklyn().getCatalog().getRootClassLoader().loadClass(type);
            } catch (ClassNotFoundException e) {
                log.debug("Class not found for type '" + type + "'; reporting 404", e);
                throw WebResourceUtils.notFound("Undefined type '%s'", type);
            }
            log.info("Entity type '{}' not defined in catalog but is on classpath; continuing", type);
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
