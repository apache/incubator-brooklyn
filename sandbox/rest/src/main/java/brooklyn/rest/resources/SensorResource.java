package brooklyn.rest.resources;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.api.SensorSummary;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newHashSet;
import java.net.URI;
import java.util.Map;
import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

@Path("/v1/applications/{application}/entities/{entity}/sensors")
public class SensorResource extends BaseResource {

  private final ApplicationManager manager;

  public SensorResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  public Iterable<SensorSummary> list(
      @PathParam("application") final String applicationName,
      @PathParam("entity") final String entityIdOrName
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return transform(filter(
        entity.getSensors().entrySet(),
        new Predicate<Map.Entry<String, Sensor<?>>>() {
          @Override
          public boolean apply(@Nullable Map.Entry<String, Sensor<?>> input) {
            return input != null && input.getValue() instanceof AttributeSensor;
          }
        }),
        new Function<Map.Entry<String, Sensor<?>>, SensorSummary>() {
          @Override
          public SensorSummary apply(Map.Entry<String, Sensor<?>> entry) {
            return new SensorSummary(application, entity, entry.getValue());
          }
        });
  }

  @GET
  @Path("{sensor}")
  public String get(
      @PathParam("application") String applicationName,
      @PathParam("entity") String entityId,
      @PathParam("sensor") String sensorName
  ) {
    Application application = getApplicationOr404(manager.registry(), applicationName);
    EntityLocal entity = getEntityOr404(application, entityId);

    Object value = entity.getAttribute(getSensorOr404(entity, sensorName));
    return (value != null) ? value.toString() : "";
  }

}
