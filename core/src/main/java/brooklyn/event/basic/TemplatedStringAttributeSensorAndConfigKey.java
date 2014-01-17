package brooklyn.event.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.ImmutableMap;

/**
 * A {@link ConfigKey} which takes a freemarker-templated string,
 * and whose value is converted to a sensor by processing the template 
 * with access to config and methods on the entity where it is set.
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
