package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;

import com.thoughtworks.xstream.XStream;

public class XmlMementoSerializer<T> implements MementoSerializer<T> {
    private final XStream xstream;
    private final ClassLoader classLoader;

    public XmlMementoSerializer(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        xstream = new XStream();
        xstream.alias("brooklyn", MutableBrooklynMemento.class);
        xstream.alias("entity", BasicEntityMemento.class);
        xstream.alias("location", BasicLocationMemento.class);
        xstream.alias("configKey", BasicConfigKey.class);
        xstream.alias("attributeSensor", BasicAttributeSensor.class);
    }
    
    @Override
    public String toString(T memento) {
        return xstream.toXML(memento);
    }

    @Override
    public T fromString(String xml) {
        return (T) xstream.fromXML(xml);
    }
}