package brooklyn.rest.transform;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.util.URLParamEncoder;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

public class SensorTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SensorTransformer.class);

    public static SensorSummary sensorSummaryForCatalog(Sensor<?> sensor) {
        return new SensorSummary(sensor.getName(), sensor.getTypeName(),
                sensor.getDescription(), null);
    }

    @SuppressWarnings("rawtypes")
    public static SensorSummary sensorSummary(Entity entity, Sensor<?> sensor) {

        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();
        String selfUri = entityUri + "/sensors/" + URLParamEncoder.encode(sensor.getName());

        MutableMap.Builder<String, URI> lb = MutableMap.<String, URI>builder()
                .put("self", URI.create(selfUri))
                .put("application", URI.create(applicationUri))
                .put("entity", URI.create(entityUri))
                .put("action:json", URI.create(selfUri));
        Set<RendererHints.Hint> hints = RendererHints.getHintsFor(sensor);
        for (RendererHints.Hint h: hints) addRendererHint(lb, h, entity, sensor);

        return new SensorSummary(sensor.getName(), sensor.getTypeName(), sensor.getDescription(), lb.build());
    }

    @SuppressWarnings("rawtypes")
    private static void addRendererHint(MutableMap.Builder<String, URI> lb, RendererHints.Hint h, Entity entity, Sensor<?> sensor) {
        if (!(h instanceof RendererHints.NamedAction))
            return;
        if (h instanceof RendererHints.NamedActionWithUrl) {
            try {
                String v = ((RendererHints.NamedActionWithUrl)h).getUrl(entity, (AttributeSensor<?>) sensor);
                if (v!=null && !v.isEmpty()) lb.putIfAbsent("action:open", URI.create(v));
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.warn("Unable to make use of URL sensor "+sensor+" on "+entity+": "+e);
            }
        }
    }
}
