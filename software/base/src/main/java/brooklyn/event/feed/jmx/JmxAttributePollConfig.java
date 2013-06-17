package brooklyn.event.feed.jmx;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class JmxAttributePollConfig<T> extends PollConfig<Object, T, JmxAttributePollConfig<T>>{

    private ObjectName objectName;
    private String attributeName;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public JmxAttributePollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        onSuccess((Function)Functions.identity());
    }

    public JmxAttributePollConfig(JmxAttributePollConfig<T> other) {
        super(other);
        this.objectName = other.objectName;
        this.attributeName = other.attributeName;
    }

    public ObjectName getObjectName() {
        return objectName;
    }
    
    public String getAttributeName() {
        return attributeName;
    }
    
    public JmxAttributePollConfig<T> objectName(ObjectName val) {
        this.objectName = val; return this;
    }
    
    public JmxAttributePollConfig<T> objectName(String val) {
        try {
            return objectName(new ObjectName(val));
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name ("+val+")", e);
        }
    }
    
    public JmxAttributePollConfig<T> attributeName(String val) {
        this.attributeName = val; return this;
    }
}
