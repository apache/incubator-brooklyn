package brooklyn.util.xstream;

import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

public class ImmutableMapConverter extends MapConverter {

    public ImmutableMapConverter(Mapper mapper) {
        super(mapper);
    }

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return ImmutableMap.class.isAssignableFrom(type);
    }

    // marshalling is the same
    // so is unmarshalling the entries

    // only differences are creating the overarching collection, which we do after the fact
    // (optimizing format on disk as opposed to in-memory), and we discard null key/values 
    // to avoid failing entirely.
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Map<?, ?> map = Maps.newLinkedHashMap();
        populateMap(reader, context, map);
        return ImmutableMap.copyOf(Maps.filterEntries(map, new Predicate<Map.Entry<?,?>>() {
                @Override public boolean apply(Entry<?, ?> input) {
                    return input != null && input.getKey() != null && input.getValue() != null;
                }}));
    }
}
