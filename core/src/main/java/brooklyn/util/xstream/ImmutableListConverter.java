package brooklyn.util.xstream;

import java.util.ArrayList;
import java.util.Collection;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

public class ImmutableListConverter extends CollectionConverter {

    public ImmutableListConverter(Mapper mapper) {
        super(mapper);
    }

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return ImmutableList.class.isAssignableFrom(type);
    }

    // marshalling is the same
    // so is unmarshalling the entries

    // only difference is creating the overarching collection, which we do after the fact
    // (optimizing format on disk as opposed to in-memory)
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Collection collection = new ArrayList();
        populateCollection(reader, context, collection);
        return ImmutableList.copyOf(collection);
    }

}
