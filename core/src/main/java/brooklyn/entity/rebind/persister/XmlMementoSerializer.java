package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Writer;

import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.xstream.XmlSerializer;

/* uses xml, cleaned up a bit
 * 
 * there is an early attempt at doing this with JSON in pull request #344 but 
 * it is not nicely deserializable, see comments at http://xstream.codehaus.org/json-tutorial.html */  
public class XmlMementoSerializer<T> extends XmlSerializer<T> implements MementoSerializer<T> {
    
    @SuppressWarnings("unused")
    private final ClassLoader classLoader;

    public XmlMementoSerializer(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        xstream.alias("brooklyn", MutableBrooklynMemento.class);
        xstream.alias("entity", BasicEntityMemento.class);
        xstream.alias("location", BasicLocationMemento.class);
        xstream.alias("configKey", BasicConfigKey.class);
        xstream.alias("attributeSensor", BasicAttributeSensor.class);
    }
    
    @Override
    public void serialize(Object object, Writer writer) {
        super.serialize(object, writer);
        try {
            writer.append("\n");
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

}