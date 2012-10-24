package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.xstream.ImmutableListConverter;
import brooklyn.util.xstream.Inet4AddressConverter;
import brooklyn.util.xstream.StringKeyMapConverter;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.XStream;

/* uses xml, cleaned up a bit
 * 
 * there is an early attempt at doing this with JSON in pull request #344 but 
 * it is not nicely deserializable, see comments at http://xstream.codehaus.org/json-tutorial.html */  
public class XmlMementoSerializer<T> implements MementoSerializer<T> {
    
    private final XStream xstream;
    
    @SuppressWarnings("unused")
    private final ClassLoader classLoader;

    public XmlMementoSerializer(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        xstream = new XStream();
        xstream.alias("brooklyn", MutableBrooklynMemento.class);
        xstream.alias("entity", BasicEntityMemento.class);
        xstream.alias("location", BasicLocationMemento.class);
        xstream.alias("configKey", BasicConfigKey.class);
        xstream.alias("attributeSensor", BasicAttributeSensor.class);
        
        // list as array list is default
        xstream.alias("map", Map.class, LinkedHashMap.class);
        xstream.alias("set", Set.class, LinkedHashSet.class);
        
        xstream.registerConverter(new StringKeyMapConverter(xstream.getMapper()), /* priority */ 10);
        xstream.alias("MutableMap", MutableMap.class);
        
        xstream.aliasType("ImmutableList", ImmutableList.class);
        xstream.registerConverter(new ImmutableListConverter(xstream.getMapper()));
        
        xstream.registerConverter(new Inet4AddressConverter());
    }
    
    @Override
    public String toString(T memento) {
        Writer writer = new StringWriter();
        xstream.toXML(memento, writer);
        try { 
            writer.append("\n"); 
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        return writer.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T fromString(String xml) {
        return (T) xstream.fromXML(xml);
    }
}