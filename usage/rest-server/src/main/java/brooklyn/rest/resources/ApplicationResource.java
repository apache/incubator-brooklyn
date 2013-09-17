package brooklyn.rest.resources;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.ACCEPTED;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.management.Task;
import brooklyn.rest.api.ApplicationApi;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.transform.ApplicationTransformer;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Collections2;


public class ApplicationResource extends AbstractBrooklynRestResource implements ApplicationApi {

  private static final Logger log = LoggerFactory.getLogger(ApplicationResource.class);
  
  private final ObjectMapper mapper = new ObjectMapper();

  /** @deprecated since 0.6.0 use {@link #fetch(String)} (with slightly different, but better semantics) */
  @Deprecated
  @Override
  public JsonNode applicationTree() {
    ArrayNode apps = mapper.createArrayNode();
    for (Application application : mgmt().getApplications())
      apps.add(recursiveTreeFromEntity(application));
    return apps;
  }
  
  private ObjectNode entityBase(Entity entity) {
      ObjectNode aRoot = mapper.createObjectNode();
      aRoot.put("name", entity.getDisplayName());
      aRoot.put("id", entity.getId());
      aRoot.put("type", entity.getEntityType().getName());
      
      Boolean serviceUp = entity.getAttribute(Attributes.SERVICE_UP);
      if (serviceUp!=null) aRoot.put("serviceUp", serviceUp);
      
      Lifecycle serviceState = entity.getAttribute(Attributes.SERVICE_STATE);
      if (serviceState!=null) aRoot.put("serviceState", serviceState.toString());
      
      String iconUrl = entity.getIconUrl();
      if (iconUrl!=null) {
          if (brooklyn().isUrlServerSideAndSafe(iconUrl)) 
              // route to server if it is a server-side url
              iconUrl = EntityTransformer.entityUri(entity)+"/icon";
          aRoot.put("iconUrl", iconUrl);
      }
      
      return aRoot;
  }
  
  private JsonNode recursiveTreeFromEntity(Entity entity) {
    ObjectNode aRoot = entityBase(entity);

    if (!entity.getChildren().isEmpty())
      aRoot.put("children", childEntitiesRecursiveAsArray(entity));
    
    return aRoot;
  }
  
  // TODO when applicationTree can be removed, replace this with an extension to EntitySummary (without links)
  private JsonNode fromEntity(Entity entity) {
    ObjectNode aRoot = entityBase(entity);
    
    aRoot.put("applicationId", entity.getApplicationId());
    
    if (entity.getParent()!=null) {
        aRoot.put("parentId", entity.getParent().getId());
    }

    if (!entity.getChildren().isEmpty())
        aRoot.put("childrenIds", childEntitiesIdAsArray(entity));

    return aRoot;
  }
  
  private ArrayNode childEntitiesRecursiveAsArray(Entity entity) {
    ArrayNode node = mapper.createArrayNode();
    for (Entity e : entity.getChildren()) {
      node.add(recursiveTreeFromEntity(e));
    }
    return node;
  }

  private ArrayNode childEntitiesIdAsArray(Entity entity) {
      ArrayNode node = mapper.createArrayNode();
      for (Entity e : entity.getChildren()) {
        node.add(e.getId());
      }
      return node;
  }

  public JsonNode fetch(String items) {
      Map<String,JsonNode> resultM = MutableMap.<String, JsonNode>of();
      for (Application application : mgmt().getApplications())
        resultM.put(application.getId(), fromEntity(application));
      if (items!=null) {
          String[] itemsO = items.split(",");
          for (String item: itemsO) {
              Entity itemE = mgmt().getEntityManager().getEntity( item.trim() );
              while (itemE != null && !(itemE instanceof Application)) {
                  resultM.put(itemE.getId(), fromEntity(itemE));
                  itemE= itemE.getParent();
              }
          }
      }
      
      ArrayNode result = mapper.createArrayNode();
      for (JsonNode n: resultM.values()) result.add(n);
      return result;
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
