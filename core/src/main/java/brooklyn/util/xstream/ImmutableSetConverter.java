package brooklyn.util.xstream;

import java.util.Collection;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

public class ImmutableSetConverter extends CollectionConverter {

    public ImmutableSetConverter(Mapper mapper) {
        super(mapper);
    }

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return ImmutableSet.class.isAssignableFrom(type);
    }

    // marshalling is the same
    // so is unmarshalling the entries

    // only differences are creating the overarching collection, which we do after the fact
    // (optimizing format on disk as opposed to in-memory), and we discard null values 
    // to avoid failing entirely.
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Collection<?> collection = Lists.newArrayList();
        populateCollection(reader, context, collection);
        return ImmutableSet.copyOf(Iterables.filter(collection, Predicates.notNull()));
    }
}
