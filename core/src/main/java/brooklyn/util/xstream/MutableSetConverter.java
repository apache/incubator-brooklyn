package brooklyn.util.xstream;

import brooklyn.util.collections.MutableSet;

import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.mapper.Mapper;

public class MutableSetConverter extends CollectionConverter {

    // Although this class seems pointless (!), without registering an explicit converter for MutableSet then the
    // declaration for Set interferes, causing MutableSet.map field to be null on deserialization.
    
    public MutableSetConverter(Mapper mapper) {
        super(mapper);
    }

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return MutableSet.class.isAssignableFrom(type);
    }

    @Override
    protected Object createCollection(Class type) {
        return new MutableSet<Object>();
    }
}
