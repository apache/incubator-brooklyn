package brooklyn.rest.resources;

import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.core.BrooklynRestResourceUtils;

public abstract class BrooklynResourceBase {

    // TODO nasty cheat
    @Deprecated
    private static final ManagementContext defaultManagementContext = new LocalManagementContext();
    
    
    private ManagementContext managementContext;
    private BrooklynRestResourceUtils brooklynRestResourceUtils;

    public synchronized ManagementContext mgmt() {
        if (managementContext!=null) return managementContext;
        // TODO read from servlet attributes?
        return defaultManagementContext;
//        throw new IllegalStateException("ManagementContext must be specified for Brooklyn Jersey Resource "+this);
    }
    
    public void injectManagementContext(ManagementContext managementContext) {
        if (this.managementContext!=null) {
            if (this.managementContext.equals(managementContext)) return;
            throw new IllegalStateException("ManagementContext cannot be changed: specified twice for Brooklyn Jersey Resource "+this);
        }
        this.managementContext = managementContext;
    }

    public synchronized BrooklynRestResourceUtils brooklyn() {
        if (brooklynRestResourceUtils!=null) return brooklynRestResourceUtils;
        brooklynRestResourceUtils = new BrooklynRestResourceUtils(mgmt());
        return brooklynRestResourceUtils;
    }
    
    
    
    
//    
//    
//    // -- TODO remove below here
//    
//    @Deprecated
//  protected static WebApplicationException notFound(String format, Object... args) {
//    throw WebResourceUtils.notFound(format, args);
//  }
//
//  @Deprecated
//  protected static WebApplicationException preconditionFailed(String format, Object... args) {
//      WebResourceUtils.notFound(format, args);
//    throw WebResourceUtils.preconditionFailed(format, args);
//  }
//  
//  /**
//   * Find an entity by querying the application tree of entities
//   */
//  @Deprecated
//  protected static EntityLocal getEntityOr404(ApplicationSummary application, String entityIdOrName) {
//    EntityLocal result = recursiveGetEntityOrNull(application.getInstance(), entityIdOrName);
//    if (result == null) {
//      throw notFound("Application '%s' has no entity with id or name '%s'",
//          application.getSpec().getName(), entityIdOrName);
//    }
//    return result;
//  }
//
//  @Deprecated
//  private static EntityLocal recursiveGetEntityOrNull(EntityLocal entity, String entityIdOrName) {
//    // TODO: switch to BFS traversal & add cycle detection
//    if (entity.getId().equals(entityIdOrName) || entity.getDisplayName().equals(entityIdOrName)) {
//      return entity;
//    }
//
//    for (Entity child : entity.getOwnedChildren()) {
//      if (child instanceof EntityLocal) {
//        EntityLocal result = recursiveGetEntityOrNull((EntityLocal) child, entityIdOrName);
//        if (result != null) {
//          return result;
//        }
//      }
//    }
//
//    return null;
//  }
//
//  @Deprecated
//  @SuppressWarnings("unchecked")
//protected static AttributeSensor<Object> getSensorOr404(EntityLocal entity, String sensorName) {
//    if (!entity.getEntityType().hasSensor(sensorName)) {
//      throw notFound("Entity '%s' has no sensor with name '%s'", entity.getId(), sensorName);
//    }
//
//    Sensor<?> sensor = entity.getEntityType().getSensor(sensorName);
//    if (!(sensor instanceof AttributeSensor)) {
//      throw notFound("Sensor '%s' is not an AttributeSensor", sensorName);
//    }
//
//    return (AttributeSensor<Object>) sensor;
//  }
//
//  @Deprecated
//  protected static ApplicationSummary getApplicationOr404(ApplicationManager manager, String applicationIdOrName) {
//      ApplicationSummary app = manager.getApp(applicationIdOrName);
//      if (app==null)
//          throw notFound("Application '%s' not found.", applicationIdOrName);
//      return app;
//  }
//
//  @Deprecated
//  protected static ApplicationSummary getApplicationOr404(Map<String, ApplicationSummary> registry, String application) {
//    if (!registry.containsKey(application))
//      throw notFound("Application '%s' not found.", application);
//
//    return registry.get(application);
//  }
}
