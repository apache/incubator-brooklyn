package brooklyn.util.xstream;

import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

public class StringKeyMapConverter extends MapConverter {

    private static final Logger log = LoggerFactory.getLogger(StringKeyMapConverter.class);
    
    public StringKeyMapConverter(Mapper mapper) {
        super(mapper);
    }

    public boolean canConvert(Class type) {
        return super.canConvert(type) || type.getName().equals(MutableMap.class.getName());
    }
    
    @Override
    protected void marshalEntry(HierarchicalStreamWriter writer, MarshallingContext context, Entry entry) {
        if (entry.getKey() instanceof String) {
            marshalStringKey(writer, context, entry);
        } else {
            super.marshalEntry(writer, context, entry);
        }
    }
    
    protected void marshalStringKey(HierarchicalStreamWriter writer, MarshallingContext context, Entry entry) {
        ExtendedHierarchicalStreamWriterHelper.startNode(writer, getEntryNodeName(), Map.Entry.class);
        writer.addAttribute("key", (String)entry.getKey());
        
        if (entry.getValue()!=null && isInlineableType(entry.getValue().getClass())) {
            writer.addAttribute("type", mapper().serializedClass(entry.getValue().getClass()));
            if (entry.getValue().getClass().isEnum())
                writer.setValue(((Enum)entry.getValue()).name());
            else
                writer.setValue(""+entry.getValue());
        } else {
            writeItem(entry.getValue(), context, writer);
        }
        
        writer.endNode();
    }

    protected boolean isInlineableType(Class<?> type) {
        return TypeCoercions.isPrimitiveOrBoxer(type) || String.class.equals(type) || type.isEnum();
    }
    
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        try {
            return super.unmarshal(reader, context);
        } catch (Throwable t) {
            t.printStackTrace();
            throw Exceptions.propagate(t);
        }
    }
    
    @Override
    protected void unmarshalEntry(HierarchicalStreamReader reader, UnmarshallingContext context, Map map) {
        String key = reader.getAttribute("key");
        if (key==null) {
            super.unmarshalEntry(reader, context, map);
        } else {
            unmarshalStringKey(reader, context, map, key);
        }
    }

    protected void unmarshalStringKey(HierarchicalStreamReader reader, UnmarshallingContext context, Map map, String key) {
        reader.moveDown();
        String type = reader.getAttribute("type");
        Object value;
        if (type==null) {
            reader.moveDown();
            value = readItem(reader, context, map);
            reader.moveUp();
        } else {
            Class typeC = mapper().realClass(type);
            try {
                value = TypeCoercions.coerce(reader.getValue(), typeC);
            } catch (Exception e) {
                log.warn("FAILED to coerce "+reader.getValue()+" to "+typeC+": "+e);
                throw Exceptions.propagate(e);
            }
        }
        reader.moveUp();
        map.put(key, value);
    }

}
