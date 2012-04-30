package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.rest.api.Application;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.net.URI;
import java.util.Map;
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
  public Iterable<URI> list(@PathParam("application") final String applicationName) {
    Application application = getApplicationOr404(manager.registry(), applicationName);

    return childEntitiesAsRefs(application, application.getInstance());
  }

  @GET
  @Path("{entity}")
  public Map<String, String> get(
      @PathParam("application") final String applicationName,
      @PathParam("entity") final String entityIdOrName
  ) {
    return ImmutableMap.of();
  }

  @GET
  @Path("{entity}/entities")
  public Iterable<URI> getChildren(
      @PathParam("application") final String applicationName,
      @PathParam("entity") final String entityIdOrName
  ) {
    Application application = getApplicationOr404(manager.registry(), applicationName);
    Entity entity = getEntityOr404(application, entityIdOrName);

    return childEntitiesAsRefs(application, entity);
  }

  private Iterable<URI> childEntitiesAsRefs(final Application application, Entity entity) {
    return Iterables.transform(entity.getOwnedChildren(),
        new Function<Entity, URI>() {
          @Override
          public URI apply(Entity input) {
            return URI.create("/v1/applications/" +
                application.getSpec().getName() + "/entities/" + input.getId());
          }
        });
  }
}
