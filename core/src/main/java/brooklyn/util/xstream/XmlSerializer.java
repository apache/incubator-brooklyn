package brooklyn.util.xstream;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.mapper.MapperWrapper;

public class XmlSerializer<T> {

    protected final XStream xstream;
    
    public XmlSerializer() {
        xstream = new XStream() {
            @Override
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                MapperWrapper result = super.wrapMapper(next);
                return XmlSerializer.this.wrapMapper(result);
            }
        };
        
        // list as array list is default
        xstream.alias("map", Map.class, LinkedHashMap.class);
        xstream.alias("set", Set.class, LinkedHashSet.class);
        
        xstream.registerConverter(new StringKeyMapConverter(xstream.getMapper()), /* priority */ 10);
        xstream.alias("MutableMap", MutableMap.class);
        xstream.alias("MutableSet", MutableSet.class);
        xstream.alias("MutableList", MutableList.class);
        
        // Needs an explicit MutableSet converter!
        // Without it, the alias for "set" seems to interfere with the MutableSet.map field, so it gets
        // a null field on deserialization.
        xstream.registerConverter(new MutableSetConverter(xstream.getMapper()));
        
        xstream.aliasType("ImmutableList", ImmutableList.class);
        xstream.registerConverter(new ImmutableListConverter(xstream.getMapper()));

        xstream.registerConverter(new EnumCaseForgivingConverter());
        xstream.registerConverter(new Inet4AddressConverter());
    }
    
    protected MapperWrapper wrapMapper(MapperWrapper next) {
        return next;
    }

    public void serialize(Object object, Writer writer) {
        xstream.toXML(object, writer);
    }

    @SuppressWarnings("unchecked")
    public T deserialize(Reader xml) {
        return (T) xstream.fromXML(xml);
    }

    public String toString(T memento) {
        Writer writer = new StringWriter();
        serialize(memento, writer);
        return writer.toString();
    }

    public T fromString(String xml) {
        return deserialize(new StringReader(xml));
    }

}
