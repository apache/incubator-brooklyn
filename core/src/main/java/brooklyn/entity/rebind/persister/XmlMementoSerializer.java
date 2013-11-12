package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.policy.EntityAdjunct;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.xstream.XmlSerializer;

import com.google.common.base.Function;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/* uses xml, cleaned up a bit
 * 
 * there is an early attempt at doing this with JSON in pull request #344 but 
 * it is not nicely deserializable, see comments at http://xstream.codehaus.org/json-tutorial.html */  
public class XmlMementoSerializer<T> extends XmlSerializer<T> implements MementoSerializer<T> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlMementoSerializer.class);

    @SuppressWarnings("unused")
    private final ClassLoader classLoader;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public XmlMementoSerializer(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        xstream.alias("brooklyn", MutableBrooklynMemento.class);
        xstream.alias("entity", BasicEntityMemento.class);
        xstream.alias("location", BasicLocationMemento.class);
        xstream.alias("configKey", BasicConfigKey.class);
        xstream.alias("attributeSensor", BasicAttributeSensor.class);
        xstream.registerConverter(new ConverterImpl(Location.class, new Function<Location,String>() {
            @Override public String apply(Location input) {
                return (input != null) ? input.getId() : null;
            }}));
        xstream.registerConverter(new ConverterImpl(Entity.class, new Function<Entity,String>() {
            @Override public String apply(Entity input) {
                return (input != null) ? input.getId() : null;
            }}));
        xstream.registerConverter(new ConverterImpl(EntityAdjunct.class, new Function<EntityAdjunct,String>() {
            @Override public String apply(EntityAdjunct input) {
                return (input != null) ? input.getId() : null;
            }}));
    }
    
    @Override
    public void serialize(Object object, Writer writer) {
        super.serialize(object, writer);
        try {
            writer.append("\n");
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    public static class ConverterImpl<T> implements Converter {
        private final AtomicBoolean hasWarned = new AtomicBoolean(false);
        private final Class<?> converatable;
        private final Function<T,String> idExtractor;
        
        ConverterImpl(Class<T> converatable, Function<T,String> idExtractor) {
            this.converatable = checkNotNull(converatable, "converatable");
            this.idExtractor = checkNotNull(idExtractor, "idExtractor");
        }
        
        @SuppressWarnings({ "rawtypes" })
        @Override
        public boolean canConvert(Class type) {
            return converatable.isAssignableFrom(type);
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (source != null) {
                if (hasWarned.compareAndSet(false, true)) {
                    LOG.warn("Cannot marshall to xml (for persistence) {} {}; should have been intercepted; unmarshalling will give null!", converatable.getSimpleName(), source);
                } else {
                    LOG.debug("Cannot marshall to xml (for persistence) {} {}; should have been intercepted; unmarshalling will give null!", converatable.getSimpleName(), source);
                }
            }
            // no-op; can't marshall this; deserializing will give null!
            writer.startNode("unserializableLocation");
            writer.setValue(idExtractor.apply((T)source));
            writer.endNode();
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            reader.moveDown();
            String id = reader.getValue();
            reader.moveUp();
            LOG.warn("Cannot unmarshall from persisted xml {} {}; should have been intercepted; returning null!", converatable.getSimpleName(), id);
            return null;
        }
    }
}
