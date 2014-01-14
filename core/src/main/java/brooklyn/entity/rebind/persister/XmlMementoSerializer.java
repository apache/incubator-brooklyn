package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.entity.trait.Identifiable;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.xstream.XmlSerializer;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.util.HierarchicalStreams;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

/* uses xml, cleaned up a bit
 * 
 * there is an early attempt at doing this with JSON in pull request #344 but 
 * it is not nicely deserializable, see comments at http://xstream.codehaus.org/json-tutorial.html */  
public class XmlMementoSerializer<T> extends XmlSerializer<T> implements MementoSerializer<T> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlMementoSerializer.class);

    public class CustomMapper extends MapperWrapper {
        private final Class<?> clazz;
        private final String alias;

        public CustomMapper(Mapper wrapped, Class<?> clazz, String alias) {
            super(wrapped);
            this.clazz = checkNotNull(clazz, "clazz");
            this.alias = checkNotNull(alias, "alias");
        }

        public String getAlias() {
            return alias;
        }

        @Override
        public String serializedClass(Class type) {
            if (type != null && clazz.isAssignableFrom(type)) {
                return alias;
            } else {
                return super.serializedClass(type);
            }
        }

        @Override
        public Class realClass(String elementName) {
            if (elementName.equals(alias)) {
                return clazz;
            } else {
                return super.realClass(elementName);
            }
        }
    }

    @SuppressWarnings("unused")
    private final ClassLoader classLoader;
    private LookupContext lookupContext;

    /**
     * serializer that can only serialize, and not deserialize 
     * (because needs management context to deserialize location/entity references).
     */
    public XmlMementoSerializer(ClassLoader classLoader) {
        this(classLoader, null);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public XmlMementoSerializer(ClassLoader classLoader, ManagementContext managementContext) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        xstream.alias("brooklyn", MutableBrooklynMemento.class);
        xstream.alias("entity", BasicEntityMemento.class);
        xstream.alias("location", BasicLocationMemento.class);
        xstream.alias("configKey", BasicConfigKey.class);
        xstream.alias("attributeSensor", BasicAttributeSensor.class);
        
        xstream.alias("entityRef", Entity.class);
        xstream.alias("locationRef", Location.class);

        xstream.registerConverter(new LocationConverter(managementContext));
        xstream.registerConverter(new EntityConverter(managementContext));
        xstream.registerConverter(new TaskConverter(xstream.getMapper()));
        // TODO policies/enrichers serialization/deserialization?!
    }
    
    // Warning: this is called in the super-class constuctor, so before this constructor!
    protected MapperWrapper wrapMapper(MapperWrapper next) {
        MapperWrapper result = new CustomMapper(next, Entity.class, "entityProxy");
        return new CustomMapper(result, Location.class, "locationProxy");
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

    public static class ConverterImpl<T extends Identifiable> implements Converter {
        private final AtomicBoolean hasWarned = new AtomicBoolean(false);
        private final Class<?> converatable;
        
        ConverterImpl(Class<T> converatable) {
            this.converatable = checkNotNull(converatable, "converatable");
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
            writer.setValue(((T)source).getId());
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

    public static abstract class IdentifiableConverter<T extends Identifiable> implements SingleValueConverter {
        protected final ManagementContext managementContext;
        private final Class<T> clazz;
        
        IdentifiableConverter(ManagementContext managementContext, Class<T> clazz) {
            this.managementContext = managementContext;
            this.clazz = clazz;
        }
        @Override
        public boolean canConvert(Class type) {
            return clazz.isAssignableFrom(type);
        }

        @Override
        public String toString(Object obj) {
            return obj == null ? null : ((Identifiable)obj).getId();
        }
        @Override
        public Object fromString(String str) {
            T result = lookup(str);
            if (result == null) {
                LOG.warn("Cannot unmarshall from persisted xml {} {}; not found in management context!", clazz.getSimpleName(), str);
            }
            return result;
        }
        
        protected abstract T lookup(String id);
    }

    public class LocationConverter extends IdentifiableConverter<Location> {
        LocationConverter(ManagementContext managementContext) {
            super(managementContext, Location.class);
        }
        @Override
        protected Location lookup(String id) {
            return (lookupContext != null) ? lookupContext.lookupLocation(id) : null;
        }
    }
    
    public class EntityConverter extends IdentifiableConverter<Entity> {
        EntityConverter(ManagementContext managementContext) {
            super(managementContext, Entity.class);
        }
        @Override
        protected Entity lookup(String id) {
            return (lookupContext != null) ? lookupContext.lookupEntity(id) : null;
        }
    }

    public class TaskConverter implements Converter {
        private final Mapper mapper;
        
        TaskConverter(Mapper mapper) {
            this.mapper = mapper;
        }
        @Override
        public boolean canConvert(Class type) {
            return Task.class.isAssignableFrom(type);
        }
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (source == null) return;
            if (((Task)source).isDone() && !((Task)source).isError()) {
                try {
                    context.convertAnother(((Task)source).get());
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                } catch (ExecutionException e) {
                    LOG.warn("Unexpected exception getting done (and non-error) task result for "+source+"; continuing", e);
                }
            } else {
                // TODO How to log sensibly, without it logging this every second?!
                return;
            }
        }
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            if (reader.hasMoreChildren()) {
                Class type = HierarchicalStreams.readClassType(reader, mapper);
                reader.moveDown();
                Object result = context.convertAnother(null, type);
                reader.moveUp();
                return result;
            } else {
                return null;
            }
        }
    }

    @Override
    public void setLookupContext(LookupContext lookupContext) {
        this.lookupContext = checkNotNull(lookupContext, "lookupContext");
    }

    @Override
    public void unsetLookupContext() {
        this.lookupContext = null;
    }
}
