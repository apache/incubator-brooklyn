package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.SensorSummary;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/sensors")
@Apidoc("Entity sensors")
@Produces("application/json")
public class SensorResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(value = "Fetch the sensor list for a specific application entity",
      responseClass = "brooklyn.rest.domain.SensorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<SensorSummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken
  ) {
    final EntityLocal entity = brooklyn().getEntity(application, entityToken);

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
            return SensorSummary.fromEntity(entity, sensor);
          }
        }));
  }

  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch sensor values in batch", notes="Returns a map of sensor name to value")
  public Map<String, String> batchSensorRead(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken
      ) {
    final EntityLocal entity = brooklyn().getEntity(application, entityToken);
    // TODO: add test
    Map<String, String> sensorMap = Maps.newHashMap();
    List<Sensor<?>> sensors = Lists.newArrayList(filter(entity.getEntityType().getSensors(),
        new Predicate<Sensor<?>>() {
          @Override
          public boolean apply(@Nullable Sensor<?> input) {
            return input instanceof AttributeSensor;
          }
        }));

    for (Sensor<?> sensor : sensors) {
      Object value = entity.getAttribute(findSensor(entity, sensor.getName()));
      // TODO type
      sensorMap.put(sensor.getName(), (value != null) ? value.toString() : "");
    }
    return sensorMap;
  }

  @GET
  @Path("/{sensor}")
  @ApiOperation(value = "Fetch sensor value", responseClass = "String")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or sensor")
  })
  public String get(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken,
          @ApiParam(value = "Sensor name", required = true)
          @PathParam("sensor") String sensorName
  ) {
      final EntityLocal entity = brooklyn().getEntity(application, entityToken);
    Object value = entity.getAttribute(findSensor(entity, sensorName));
    return (value != null) ? value.toString() : "";
  }

  private AttributeSensor<?> findSensor(EntityLocal entity, String name) {
      Sensor<?> s = entity.getEntityType().getSensor(name);
      if (s instanceof AttributeSensor) return (AttributeSensor<?>) s;
      return new BasicAttributeSensor<Object>(Object.class, name);
  }

}
