package brooklyn.rest.resources;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.api.Application;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

@Path("/applications/{application}/effectors")
public class EffectorResource {

  private final ApplicationManager manager;

  public EffectorResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  public Map<String, Set<URI>> listEffectors(
      @PathParam("application") final String applicationName
  ) {
    final Application application = getApplicationOr404(applicationName);

    Map<String, Set<URI>> results = Maps.newHashMap();
    for (final Entity entity : application.getInstance().getOwnedChildren()) {
      if (entity instanceof EntityLocal) {
        results.put(entity.getDisplayName(),
            Sets.newHashSet(transform(
                ((EntityLocal) entity).getEffectors().entrySet(),
                new Function<Map.Entry<String, Effector<?>>, URI>() {
                  @Override
                  public URI apply(Map.Entry<String, Effector<?>> entry) {
                    return URI.create(String.format("/applications/%s/effectors/%s/%s",
                        applicationName, entity.getDisplayName(), entry.getValue().getName()));
                  }
                })));
      }
    }

    return results;
  }

  @POST
  @Path("{entity}/{effector}")
  public void triggerEffector(
      @PathParam("application") String applicationName,
      @PathParam("entity") String entityName,
      @PathParam("effector") String effector
  ) {

  }

  public EntityLocal getEntityLocalOr404(Application application, String entityName) {

    // Only scan the first level of children entities

    for (Entity entity : application.getInstance().getOwnedChildren()) {
      if (entity.getDisplayName().equals(entityName)) {
        return (EntityLocal) entity;
      }
    }

    throw new WebApplicationException(Response.Status.NOT_FOUND);
  }

  private Application getApplicationOr404(String application) {
    if (!manager.registry().containsKey(application))
      throw new WebApplicationException(Response.Status.NOT_FOUND);

    Application current = manager.registry().get(application);
    if (current.getStatus() != Application.Status.RUNNING)
      throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
    return current;
  }
}
