package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.api.Application;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public abstract class BaseResource {

  protected EntityLocal getEntityLocalOr404(Application application, String entityName) {

    // Only scan the first level of children entities

    for (Entity entity : application.getInstance().getOwnedChildren()) {
      if (entity.getDisplayName().equals(entityName)) {
        return (EntityLocal) entity;
      }
    }

    throw new WebApplicationException(Response.Status.NOT_FOUND);
  }

  protected Application getApplicationOr404(Map<String, Application> registry, String application) {
    if (!registry.containsKey(application))
      throw new WebApplicationException(Response.Status.NOT_FOUND);

    Application current = registry.get(application);
    if (current.getStatus() != Application.Status.RUNNING)
      throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
    return current;
  }
}
