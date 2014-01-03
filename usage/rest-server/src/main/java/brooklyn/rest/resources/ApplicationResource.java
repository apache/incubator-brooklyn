package brooklyn.rest.resources;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
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
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.Collections2;


public class ApplicationResource extends AbstractBrooklynRestResource implements ApplicationApi {

  private static final Logger log = LoggerFactory.getLogger(ApplicationResource.class);
  
  /** @deprecated since 0.6.0 use {@link #fetch(String)} (with slightly different, but better semantics) */
  @Deprecated
  @Override
  public JsonNode applicationTree() {
    ArrayNode apps = mapper().createArrayNode();
    for (Application application : mgmt().getApplications())
      apps.add(recursiveTreeFromEntity(application));
    return apps;
  }
  
  private ObjectNode entityBase(Entity entity) {
      ObjectNode aRoot = mapper().createObjectNode();
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
        aRoot.put("children", childEntitiesIdAndNameAsArray(entity));

    return aRoot;
  }
  
  private ArrayNode childEntitiesRecursiveAsArray(Entity entity) {
    ArrayNode node = mapper().createArrayNode();
    for (Entity e : entity.getChildren()) {
      node.add(recursiveTreeFromEntity(e));
    }
    return node;
  }

  private ArrayNode childEntitiesIdAndNameAsArray(Entity entity) {
      ArrayNode node = mapper().createArrayNode();
      for (Entity e : entity.getChildren()) {
          ObjectNode holder = mapper().createObjectNode();
          holder.put("id", e.getId());
          holder.put("name", e.getDisplayName());
          node.add(holder);
      }
      return node;
  }

  @Override
  public JsonNode fetch(String entityIds) {
      Map<String, JsonNode> jsonEntitiesById = MutableMap.of();
      for (Application application : mgmt().getApplications())
          jsonEntitiesById.put(application.getId(), fromEntity(application));
      if (entityIds != null) {
          for (String entityId: entityIds.split(",")) {
              Entity entity = mgmt().getEntityManager().getEntity(entityId.trim());
              while (entity != null && entity.getParent() != null) {
                  jsonEntitiesById.put(entity.getId(), fromEntity(entity));
                  entity = entity.getParent();
              }
          }
      }
      
      ArrayNode result = mapper().createArrayNode();
      for (JsonNode n: jsonEntitiesById.values()) result.add(n);
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

  public Response create(ApplicationSpec applicationSpec) {
      return createFromAppSpec(applicationSpec);
  }

  /** @deprecated since 0.7.0 see #create */ @Deprecated 
  protected Response createFromAppSpec(ApplicationSpec applicationSpec) {
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
  public Response createFromYaml(String yaml) {
      log.debug("Creating app from yaml");
      Reader input = new StringReader(yaml);
      AssemblyTemplate at = camp().pdp().registerDeploymentPlan(input);
      return launch(at);
  }

  private Response launch(AssemblyTemplate at) {
    try {
          AssemblyTemplateInstantiator instantiator = at.getInstantiator().newInstance();
          Assembly assembly;
          Task<?> task = null;
          if (instantiator instanceof BrooklynAssemblyTemplateInstantiator) {
              Application app = ((BrooklynAssemblyTemplateInstantiator) instantiator).create(at, camp());
              assembly = camp().assemblies().get(app.getApplicationId());
              
              task = Entities.invokeEffector((EntityLocal)app, app, Startable.START,
                  // locations already set in the entities themselves;
                  // TODO make it so that this arg does not have to be supplied to START !
                  MutableMap.of("locations", MutableList.of()));
          } else {
              assembly = instantiator.instantiate(at, camp());
          }
          Entity app = mgmt().getEntityManager().getEntity(assembly.getId());
          log.info("Launched from YAML: "+assembly+" ("+task+")");

          URI ref = URI.create(app.getApplicationId());
          ResponseBuilder response = created(ref);
          if (task!=null)
              response.entity(TaskTransformer.FROM_TASK.apply(task));
          return response.build();
          
      } catch (Exception e) {
          throw Exceptions.propagate(e);
      }
}
  
  @Override
  public Response createPoly(byte[] inputToAutodetectType) {
      log.debug("Creating app from autodetecting input");
      try {
          ApplicationSpec appSpec = mapper().readValue(inputToAutodetectType, ApplicationSpec.class);
          return createFromAppSpec(appSpec);
      } catch (Exception e) {
          log.debug("Input is not legacy ApplicationSpec JSON (will try others): "+e);
      }
      
      // TODO not json - try ZIP, etc
      
      // finally try yaml
      AssemblyTemplate template = null;
      try {
          template = camp().pdp().registerDeploymentPlan(new StringReader(new String(inputToAutodetectType)));
      } catch (Exception e) {
          log.debug("Input is not valid YAML: "+e);
      }
      if (template!=null)
          return launch(template);
      
      return Response.serverError().entity("Unsupported format; not able to autodetect.").build();
  }

  @Override
    public Response createFromForm(String contents) {
        log.debug("Creating app from form");
        return createPoly(contents.getBytes());
    }
  
  @Override
  public Response delete(String application) {
      Task<?> t = brooklyn().destroy(brooklyn().getApplication(application));
      TaskSummary ts = TaskTransformer.FROM_TASK.apply(t);
      return status(ACCEPTED).entity(ts).build();
  }

  @Override
  public void reloadBrooklynProperties() {
      brooklyn().reloadBrooklynProperties();
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
      if (!brooklyn().getLocationRegistry().canMaybeResolve(locationId)) {
        throw WebResourceUtils.notFound("Undefined location '%s'", locationId);
      }
    }
  }

}
