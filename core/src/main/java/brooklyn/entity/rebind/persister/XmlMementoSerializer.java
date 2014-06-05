package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.effector.EffectorAndBody;
import brooklyn.entity.effector.EffectorTasks.EffectorBodyTaskFactory;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;
import brooklyn.entity.rebind.dto.BasicEnricherMemento;
import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.BasicPolicyMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.entity.trait.Identifiable;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
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

    @SuppressWarnings("unused")
    private final ClassLoader classLoader;
    private LookupContext lookupContext;

    public XmlMementoSerializer(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        
        // old (deprecated in 070? or earlier) single-file persistence uses this keyword; TODO remove soon
        xstream.alias("brooklyn", MutableBrooklynMemento.class);
        
        xstream.alias("entity", BasicEntityMemento.class);
        xstream.alias("location", BasicLocationMemento.class);
        xstream.alias("policy", BasicPolicyMemento.class);
        xstream.alias("enricher", BasicEnricherMemento.class);
        xstream.alias("configKey", BasicConfigKey.class);
        xstream.alias("attributeSensor", BasicAttributeSensor.class);
        
        xstream.alias("effector", Effector.class);
        xstream.addDefaultImplementation(EffectorAndBody.class, Effector.class);
        xstream.alias("parameter", BasicParameterType.class);
        xstream.addDefaultImplementation(EffectorBodyTaskFactory.class, EffectorTaskFactory.class);
        
        xstream.alias("entityRef", Entity.class);
        xstream.alias("locationRef", Location.class);
        xstream.alias("policyRef", Policy.class);
        xstream.alias("enricherRef", Enricher.class);

        xstream.registerConverter(new LocationConverter());
        xstream.registerConverter(new PolicyConverter());
        xstream.registerConverter(new EnricherConverter());
        xstream.registerConverter(new EntityConverter());
        xstream.registerConverter(new TaskConverter(xstream.getMapper()));
    }
    
    // Warning: this is called in the super-class constuctor, so before this constructor!
    @Override
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

    @Override
    public void setLookupContext(LookupContext lookupContext) {
        this.lookupContext = checkNotNull(lookupContext, "lookupContext");
    }

    @Override
    public void unsetLookupContext() {
        this.lookupContext = null;
    }
    
    /**
     * For changing the tag used for anything that implements/extends the given type.
     * Necessary for using EntityRef rather than the default "dynamic-proxy" tag.
     * 
     * @author aled
     */
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
        public String serializedClass(@SuppressWarnings("rawtypes") Class type) {
            if (type != null && clazz.isAssignableFrom(type)) {
                return alias;
            } else {
                return super.serializedClass(type);
            }
        }

        @Override
        public Class<?> realClass(String elementName) {
            if (elementName.equals(alias)) {
                return clazz;
            } else {
                return super.realClass(elementName);
            }
        }
    }

    public abstract class IdentifiableConverter<IT extends Identifiable> implements SingleValueConverter {
        private final Class<IT> clazz;
        
        IdentifiableConverter(Class<IT> clazz) {
            this.clazz = clazz;
        }
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
            boolean result = clazz.isAssignableFrom(type);
            return result;
        }

        @Override
        public String toString(Object obj) {
            return obj == null ? null : ((Identifiable)obj).getId();
        }
        @Override
        public Object fromString(String str) {
            if (lookupContext == null) {
                LOG.warn("Cannot unmarshall from persisted xml {} {}; no lookup context supplied!", clazz.getSimpleName(), str);
                return null;
            } else {
                return lookup(str);
            }
        }
        
        protected abstract IT lookup(String id);
    }

    public class LocationConverter extends IdentifiableConverter<Location> {
        LocationConverter() {
            super(Location.class);
        }
        @Override
        protected Location lookup(String id) {
            return lookupContext.lookupLocation(id);
        }
    }

    public class PolicyConverter extends IdentifiableConverter<Policy> {
        PolicyConverter() {
            super(Policy.class);
        }
        @Override
        protected Policy lookup(String id) {
            return lookupContext.lookupPolicy(id);
        }
    }

    public class EnricherConverter extends IdentifiableConverter<Enricher> {
        EnricherConverter() {
            super(Enricher.class);
        }
        @Override
        protected Enricher lookup(String id) {
            return lookupContext.lookupEnricher(id);
        }
    }
    
    public class EntityConverter extends IdentifiableConverter<Entity> {
        EntityConverter() {
            super(Entity.class);
        }
        @Override
        protected Entity lookup(String id) {
            return lookupContext.lookupEntity(id);
        }
    }

    public class TaskConverter implements Converter {
        private final Mapper mapper;
        
        TaskConverter(Mapper mapper) {
            this.mapper = mapper;
        }
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
            return Task.class.isAssignableFrom(type);
        }
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (source == null) return;
            if (((Task<?>)source).isDone() && !((Task<?>)source).isError()) {
                try {
                    context.convertAnother(((Task<?>)source).get());
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
                Class<?> type = HierarchicalStreams.readClassType(reader, mapper);
                reader.moveDown();
                Object result = context.convertAnother(null, type);
                reader.moveUp();
                return result;
            } else {
                return null;
            }
        }
    }
}
