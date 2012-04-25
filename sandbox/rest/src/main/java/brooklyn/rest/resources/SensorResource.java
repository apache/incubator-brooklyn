package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.rest.api.Application;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import static com.google.common.collect.Sets.newHashSet;
import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

@Path("/applications/{application}/sensors")
public class SensorResource {

  private final ApplicationManager manager;

  public SensorResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  public Map<String, Set<URI>> listAllSensors(@PathParam("application") final String applicationName) {
    Application current = getApplicationOr404(applicationName);

    // Get sensors only for the first level of children entities

    Map<String, Set<URI>> result = Maps.newHashMap();
    for (final Entity entity : current.getInstance().getOwnedChildren()) {
      if (entity instanceof EntityLocal) {
        result.put(entity.getDisplayName(),
            newHashSet(transform(filter(
                ((EntityLocal) entity).getSensors().entrySet(),
                new Predicate<Map.Entry<String, Sensor<?>>>() {
                  @Override
                  public boolean apply(@Nullable Map.Entry<String, Sensor<?>> input) {
                    return input != null && input.getValue() instanceof AttributeSensor;
                  }
                }),
                new Function<Map.Entry<String, Sensor<?>>, URI>() {
                  @Override
                  public URI apply(Map.Entry<String, Sensor<?>> input) {
                    return URI.create(String.format("/applications/%s/sensors/%s/%s",
                        applicationName, entity.getDisplayName(), input.getKey()));
                  }
                })));
      }
    }

    return result;
  }


  @GET
  @Path("{entity}/{sensor}")
  public String getSensor(
      @PathParam("application") String applicationName,
      @PathParam("entity") String entityName,
      @PathParam("sensor") String sensorName
  ) {
    Application application = getApplicationOr404(applicationName);
    EntityLocal entity = getEntityLocalOr404(application, entityName);

    if (!entity.getSensors().containsKey(sensorName)) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    Sensor<?> sensor = entity.getSensors().get(sensorName);
    if (!(sensor instanceof AttributeSensor)) {
      throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
    }

    Object value = entity.getAttribute((AttributeSensor<Object>) sensor);
    return (value != null) ? value.toString() : "";
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
