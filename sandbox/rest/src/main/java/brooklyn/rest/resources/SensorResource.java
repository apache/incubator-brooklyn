package brooklyn.rest.resources;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.SensorSummary;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities/{entity}/sensors")
@Api(value = "/v1/applications/{application}/entities/{entity}/sensors", description = "Manage sensors for each application entity")
@Produces("application/json")
public class SensorResource extends BaseResource {

  private final ApplicationManager manager;

  public SensorResource(ApplicationManager manager) {
    this.manager = checkNotNull(manager, "manager");
  }

  @GET
  @ApiOperation(value = "Fetch the sensor list for a specific application entity",
      responseClass = "brooklyn.rest.api.SensorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Missing application or entity")
  })
  public List<SensorSummary> list(
      @ApiParam(value = "Application name", required = true)
      @PathParam("application") final String applicationName,
      @ApiParam(value = "Entity name", required = true)
      @PathParam("entity") final String entityIdOrName
  ) {
    final Application application = getApplicationOr404(manager.registry(), applicationName);
    final EntityLocal entity = getEntityOr404(application, entityIdOrName);

    return Lists.newArrayList(transform(filter(
        entity.getEntityType().getSensors(),
        new Predicate<Sensor<?>>() {
          @Override
          public boolean apply(@Nullable Sensor<?> input) {
            return input instanceof AttributeSensor;
          }
        }),
        new Function<Sensor<?>, SensorSummary>() {
          @Override
          public SensorSummary apply(Sensor<?> sensor) {
            return new SensorSummary(application, entity, sensor);
          }
        }));
  }

  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch sensor details in batch")
  public Map<String, String> batchSensorRead(
      @ApiParam(value = "Application name", required = true)
      @PathParam("application") String applicationName,
      @ApiParam(value = "Entity name", required = true)
      @PathParam("entity") String entityId) {
    // TODO: add test
    Map<String, String> sensorMap = Maps.newHashMap();
    Application application = getApplicationOr404(manager.registry(), applicationName);
    EntityLocal entity = getEntityOr404(application, entityId);
    List<Sensor<?>> sensors = Lists.newArrayList(filter(entity.getEntityType().getSensors(),
        new Predicate<Sensor<?>>() {
          @Override
          public boolean apply(@Nullable Sensor<?> input) {
            return input instanceof AttributeSensor;
          }
        }));

    for (Sensor<?> sensor : sensors) {
      Object value = entity.getAttribute(getSensorOr404(entity, sensor.getName()));
      sensorMap.put(sensor.getName(), (value != null) ? value.toString() : "");
    }
    return sensorMap;
  }

  @GET
  @Path("/{sensor}")
  @ApiOperation(value = "Fetch sensor details", responseClass = "String")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Application, entity or sensor not found")
  })
  public String get(
      @ApiParam(value = "Application name", required = true)
      @PathParam("application") String applicationName,
      @ApiParam(value = "Entity name", required = true)
      @PathParam("entity") String entityId,
      @ApiParam(value = "Sensor name", required = true)
      @PathParam("sensor") String sensorName
  ) {
    Application application = getApplicationOr404(manager.registry(), applicationName);
    EntityLocal entity = getEntityOr404(application, entityId);

    Object value = entity.getAttribute(getSensorOr404(entity, sensorName));
    return (value != null) ? value.toString() : "";
  }

}
