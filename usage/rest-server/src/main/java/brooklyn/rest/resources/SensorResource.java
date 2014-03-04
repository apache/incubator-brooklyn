package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.rest.api.SensorApi;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.transform.SensorTransformer;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class SensorResource extends AbstractBrooklynRestResource implements SensorApi {

  @Override
  public List<SensorSummary> list(final String application, final String entityToken) {
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
            return SensorTransformer.sensorSummary(entity, sensor);
          }
        }));
  }

  @Override
  public Map<String, Object> batchSensorRead(final String application, final String entityToken) {
    final EntityLocal entity = brooklyn().getEntity(application, entityToken);
    Map<String, Object> sensorMap = Maps.newHashMap();
    List<Sensor<?>> sensors = Lists.newArrayList(filter(entity.getEntityType().getSensors(),
        new Predicate<Sensor<?>>() {
          @Override
          public boolean apply(@Nullable Sensor<?> input) {
            return input instanceof AttributeSensor;
          }
        }));

    for (Sensor<?> sensor : sensors) {
      Object value = entity.getAttribute(findSensor(entity, sensor.getName()));
      sensorMap.put(sensor.getName(), getValueForDisplay(value, true, false));
    }
    return sensorMap;
  }

    protected Object get(boolean preferJson, String application, String entityToken, String sensorName) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        Object value = entity.getAttribute(findSensor(entity, sensorName));
        
        return getValueForDisplay(value, preferJson, true);
    }

    @Override
    public String getPlain(String application, String entityToken, String sensorName) {
        return (String) get(false, application, entityToken, sensorName);
    }
  
    @Override
    public Object get(final String application, final String entityToken, String sensorName) {
        return get(true, application, entityToken, sensorName);
    }

  private AttributeSensor<?> findSensor(EntityLocal entity, String name) {
      Sensor<?> s = entity.getEntityType().getSensor(name);
      if (s instanceof AttributeSensor) return (AttributeSensor<?>) s;
      return new BasicAttributeSensor<Object>(Object.class, name);
  }

}
