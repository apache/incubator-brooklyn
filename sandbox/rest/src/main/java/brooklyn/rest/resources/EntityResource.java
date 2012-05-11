package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Iterables;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/v1/applications/{application}/entities")
public class EntityResource extends BaseResource {

  private final ApplicationManager manager;

  public EntityResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  public Iterable<EntitySummary> list(@PathParam("application") final String applicationName) {
    Application application = getApplicationOr404(manager.registry(), applicationName);

    return summaryForChildrenEntities(application, application.getInstance());
  }

  @GET
  @Path("{entity}")
  public EntitySummary get(
      @PathParam("application") String applicationName,
      @PathParam("entity") String entityIdOrName
  ) {
    Application application = getApplicationOr404(manager.registry(), applicationName);
    EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return new EntitySummary(application, entity);
  }

  @GET
  @Path("{entity}/entities")
  public Iterable<EntitySummary> getChildren(
      @PathParam("application") final String applicationName,
      @PathParam("entity") final String entityIdOrName
  ) {
    Application application = getApplicationOr404(manager.registry(), applicationName);
    Entity entity = getEntityOr404(application, entityIdOrName);

    return summaryForChildrenEntities(application, entity);
  }

  private Iterable<EntitySummary> summaryForChildrenEntities(final Application application, Entity rootEntity) {
    return Iterables.transform(rootEntity.getOwnedChildren(),
        new Function<Entity, EntitySummary>() {
          @Override
          public EntitySummary apply(Entity entity) {
            return new EntitySummary(application, entity);
          }
        });
  }
}
