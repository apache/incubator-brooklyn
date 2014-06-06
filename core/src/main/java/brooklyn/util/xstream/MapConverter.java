package brooklyn.util.xstream;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.ReferencingMarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

/** equivalent to super, but cleaner methods, overridable, logging, and some retries */
public class MapConverter extends com.thoughtworks.xstream.converters.collections.MapConverter {

    private static final Logger log = LoggerFactory.getLogger(MapConverter.class);
    
    public MapConverter(Mapper mapper) {
        super(mapper);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Map map = (Map) source;
        try {
            for (Iterator iterator = map.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                marshalEntry(writer, context, entry);
            }
        } catch (ConcurrentModificationException e) {
            log.warn("Map "
                // seems there is no non-deprecated way to get the path...
                + (context instanceof ReferencingMarshallingContext ? "at "+((ReferencingMarshallingContext)context).currentPath() : "")
                + "["+source+"] modified while serializing; trying alternate technique");
            ImmutableList entries = ImmutableList.copyOf(map.entrySet());
            // FIXME i think this will probably cause bogus output as it will have written some non-terminated things ... but we can try!
            for (Iterator iterator = entries.iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                marshalEntry(writer, context, entry);                
            }
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
            reader.moveDown();
            unmarshalEntry(reader, context, map);
            reader.moveUp();
        }
    }

    protected void unmarshalEntry(HierarchicalStreamReader reader, UnmarshallingContext context, Map map) {
        reader.moveDown();
        Object key = readItem(reader, context, map);
        reader.moveUp();

        reader.moveDown();
        Object value = readItem(reader, context, map);
        reader.moveUp();

        map.put(key, value);
    }

}
