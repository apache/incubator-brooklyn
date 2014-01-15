package brooklyn.event.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.event.Sensor;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.ImmutableMap;

/**
 * A {@link Sensor} describing a port on a system,
 * with a {@link ConfigKey} which can be configured with a port range
 * (either a number e.g. 80, or a string e.g. "80" or "8080-8089" or even "80, 8080-8089, 8800+", or a list of these).
 * <p>
 * To convert at runtime a single port is chosen, respecting the entity.
 */
public class TemplatedStringAttributeSensorAndConfigKey extends BasicAttributeSensorAndConfigKey<String> {
    private static final long serialVersionUID = 4680651022807491321L;
    
    public static final Logger LOG = LoggerFactory.getLogger(TemplatedStringAttributeSensorAndConfigKey.class);

    public TemplatedStringAttributeSensorAndConfigKey(String name) {
        this(name, name, null);
    }
    public TemplatedStringAttributeSensorAndConfigKey(String name, String description) {
        this(name, description, null);
    }
    public TemplatedStringAttributeSensorAndConfigKey(String name, String description, String defaultValue) {
        super(String.class, name, description, defaultValue);
    }
    public TemplatedStringAttributeSensorAndConfigKey(TemplatedStringAttributeSensorAndConfigKey orig, String defaultValue) {
        super(orig, defaultValue);
    }
    
    protected String convertConfigToSensor(String value, Entity entity) {
        return TemplateProcessor.processTemplateContents(value, (EntityInternal)entity, ImmutableMap.<String,Object>of());
    }
    
}
