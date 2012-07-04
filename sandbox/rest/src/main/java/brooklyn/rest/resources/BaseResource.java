package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.rest.api.ApiError;
import brooklyn.rest.api.Application;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.Map;

public abstract class BaseResource {

  protected WebApplicationException notFound(String format, Object... args) {
    throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
        .entity(new ApiError(String.format(format, args))).build());
  }

  protected WebApplicationException preconditionFailed(String format, Object... args) {
    throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED)
        .entity(new ApiError(String.format(format, args))).build());
  }

  /**
   * Find an entity by querying the application tree of entities
   */
  protected EntityLocal getEntityOr404(Application application, String entityIdOrName) {
    EntityLocal result = recursiveGetEntityOrNull(application.getInstance(), entityIdOrName);
    if (result == null) {
      throw notFound("Application '%s' has no entity with id or name '%s'",
          application.getSpec().getName(), entityIdOrName);
    }
    return result;
  }

  private EntityLocal recursiveGetEntityOrNull(EntityLocal entity, String entityIdOrName) {
    // TODO: switch to BFS traversal & add cycle detection
    if (entity.getId().equals(entityIdOrName) || entity.getDisplayName().equals(entityIdOrName)) {
      return entity;
    }

    for (Entity child : entity.getOwnedChildren()) {
      if (child instanceof EntityLocal) {
        EntityLocal result = recursiveGetEntityOrNull((EntityLocal) child, entityIdOrName);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  protected AttributeSensor<Object> getSensorOr404(EntityLocal entity, String sensorName) {
    if (!entity.getSensors().containsKey(sensorName)) {
      throw notFound("Entity '%s' has no sensor with name '%s'", entity.getId(), sensorName);
    }

    Sensor<?> sensor = entity.getSensors().get(sensorName);
    if (!(sensor instanceof AttributeSensor)) {
      throw notFound("Sensor '%s' is not an AttributeSensor", sensorName);
    }

    return (AttributeSensor<Object>) sensor;
  }

  protected Application getApplicationOr404(Map<String, Application> registry, String application) {
    if (!registry.containsKey(application))
      throw notFound("Application '%s' not found.", application);

    return registry.get(application);
  }
}
