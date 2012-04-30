package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.rest.api.Application;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public abstract class BaseResource {

  protected EntityLocal getEntityOr404(Application application, String entityIdOrName) {

    // TODO full tree scan

    for (Entity entity : application.getInstance().getOwnedChildren()) {
      if (entity.getId().equals(entityIdOrName) || entity.getDisplayName().equals(entityIdOrName)) {
        return (EntityLocal) entity;
      }
    }

    throw new WebApplicationException(Response.Status.NOT_FOUND);
  }

  protected AttributeSensor<Object> getSensorOr404(EntityLocal entity, String sensorName) {
    if (!entity.getSensors().containsKey(sensorName)) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    Sensor<?> sensor = entity.getSensors().get(sensorName);
    if (!(sensor instanceof AttributeSensor)) {
      throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
    }

    return (AttributeSensor<Object>) sensor;
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
