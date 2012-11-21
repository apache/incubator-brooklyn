package brooklyn.util.xstream;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.XStream;

public class XmlSerializer<T> {

    protected final XStream xstream;
    
    public XmlSerializer() {
        xstream = new XStream();
        
        // list as array list is default
        xstream.alias("map", Map.class, LinkedHashMap.class);
        xstream.alias("set", Set.class, LinkedHashSet.class);
        
        xstream.registerConverter(new StringKeyMapConverter(xstream.getMapper()), /* priority */ 10);
        xstream.alias("MutableMap", MutableMap.class);
        
        xstream.aliasType("ImmutableList", ImmutableList.class);
        xstream.registerConverter(new ImmutableListConverter(xstream.getMapper()));
        
        xstream.registerConverter(new EnumCaseForgivingConverter());
        xstream.registerConverter(new Inet4AddressConverter());
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
