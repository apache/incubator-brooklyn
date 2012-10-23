package brooklyn.util.xstream;

import java.util.Iterator;
import java.util.Map;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

/** equivalent to super, but cleaner methods */
public class MapConverter extends com.thoughtworks.xstream.converters.collections.MapConverter {

    public MapConverter(Mapper mapper) {
        super(mapper);
    }

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Map map = (Map) source;
        for (Iterator iterator = map.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            marshalEntry(writer, context, entry);
        }
    }

    protected String getEntryNodeName() { return mapper().serializedClass(Map.Entry.class); }
    
    protected void marshalEntry(HierarchicalStreamWriter writer, MarshallingContext context, Map.Entry entry) {
        ExtendedHierarchicalStreamWriterHelper.startNode(writer, getEntryNodeName(), Map.Entry.class);

        writeItem(entry.getKey(), context, writer);
        writeItem(entry.getValue(), context, writer);

        writer.endNode();
    }

    protected void populateMap(HierarchicalStreamReader reader, UnmarshallingContext context, Map map) {
        while (reader.hasMoreChildren()) {
            unmarshalEntry(reader, context, map);
        }
    }

    protected void unmarshalEntry(HierarchicalStreamReader reader, UnmarshallingContext context, Map map) {
        reader.moveDown();

        reader.moveDown();
        Object key = readItem(reader, context, map);
        reader.moveUp();

        reader.moveDown();
        Object value = readItem(reader, context, map);
        reader.moveUp();

        map.put(key, value);

        reader.moveUp();
    }

}
