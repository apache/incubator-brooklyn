/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObjectSpec;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.Policy;

import brooklyn.catalog.internal.CatalogBundleDto;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.effector.EffectorAndBody;
import brooklyn.entity.effector.EffectorTasks.EffectorBodyTaskFactory;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;
import brooklyn.entity.rebind.dto.BasicCatalogItemMemento;
import brooklyn.entity.rebind.dto.BasicEnricherMemento;
import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicFeedMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.BasicPolicyMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.entity.trait.Identifiable;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.management.classloading.BrooklynClassLoadingContextSequential;
import brooklyn.management.classloading.ClassLoaderFromBrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;
import brooklyn.util.xstream.XmlSerializer;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.core.ReferencingMarshallingContext;
import com.thoughtworks.xstream.core.util.HierarchicalStreams;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.path.PathTrackingReader;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

/* uses xml, cleaned up a bit
 * 
 * there is an early attempt at doing this with JSON in pull request #344 but 
 * it is not nicely deserializable, see comments at http://xstream.codehaus.org/json-tutorial.html */  
public class XmlMementoSerializer<T> extends XmlSerializer<T> implements MementoSerializer<T> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlMementoSerializer.class);

    private final ClassLoader classLoader;
    private LookupContext lookupContext;

    public XmlMementoSerializer(ClassLoader classLoader) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        xstream.setClassLoader(this.classLoader);
        
        // old (deprecated in 070? or earlier) single-file persistence uses this keyword; TODO remove soon in 080 ?
        xstream.alias("brooklyn", MutableBrooklynMemento.class);
        
        xstream.alias("entity", BasicEntityMemento.class);
        xstream.alias("location", BasicLocationMemento.class);
        xstream.alias("policy", BasicPolicyMemento.class);
        xstream.alias("feed", BasicFeedMemento.class);
        xstream.alias("enricher", BasicEnricherMemento.class);
        xstream.alias("configKey", BasicConfigKey.class);
        xstream.alias("catalogItem", BasicCatalogItemMemento.class);
        xstream.alias("bundle", CatalogBundleDto.class);
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
        xstream.registerConverter(new FeedConverter());
        xstream.registerConverter(new CatalogItemConverter());
        xstream.registerConverter(new SpecConverter());

        xstream.registerConverter(new ManagementContextConverter());
        xstream.registerConverter(new TaskConverter(xstream.getMapper()));
    
        //For compatibility with existing persistence stores content.
        xstream.aliasField("registeredTypeName", BasicCatalogItemMemento.class, "symbolicName");
        xstream.registerLocalConverter(BasicCatalogItemMemento.class, "libraries", new CatalogItemLibrariesConverter());
    }
    
    // Warning: this is called in the super-class constuctor, so before this constructor!
    @Override
    protected MapperWrapper wrapMapper(MapperWrapper next) {
        MapperWrapper mapper = super.wrapMapper(next);
        mapper = new CustomMapper(mapper, Entity.class, "entityProxy");
        mapper = new CustomMapper(mapper, Location.class, "locationProxy");
        return mapper;
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
                LOG.warn("Cannot unmarshal from persisted xml {} {}; no lookup context supplied!", clazz.getSimpleName(), str);
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
    
    public class FeedConverter extends IdentifiableConverter<Feed> {
        FeedConverter() {
            super(Feed.class);
        }
        @Override
        protected Feed lookup(String id) {
            return lookupContext.lookupFeed(id);
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

    @SuppressWarnings("rawtypes")
    public class CatalogItemConverter extends IdentifiableConverter<CatalogItem> {
        CatalogItemConverter() {
            super(CatalogItem.class);
        }
        @Override
        protected CatalogItem<?,?> lookup(String id) {
            return lookupContext.lookupCatalogItem(id);
        }
    }


    static boolean loggedTaskWarning = false;
    public class TaskConverter implements Converter {
        private final Mapper mapper;
        
        TaskConverter(Mapper mapper) {
            this.mapper = mapper;
        }
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
            return Task.class.isAssignableFrom(type);
        }
        @SuppressWarnings("deprecation")
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (source == null) return;
            if (((Task<?>)source).isDone() && !((Task<?>)source).isError()) {
                try {
                    context.convertAnother(((Task<?>)source).get());
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                } catch (ExecutionException e) {
                    LOG.warn("Unexpected exception getting done (and non-error) task result for "+source+"; continuing: "+e, e);
                }
            } else {
                // TODO How to log sensibly, without it logging this every second?!
                // jun 2014, have added a "log once" which is not ideal but better than the log never behaviour
                if (!loggedTaskWarning) {
                    LOG.warn("Intercepting and skipping request to serialize a Task"
                        + (context instanceof ReferencingMarshallingContext ? " at "+((ReferencingMarshallingContext)context).currentPath() : "")+
                        " (only logging this once): "+source);
                    loggedTaskWarning = true;
                }
                
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
    
    public class ManagementContextConverter implements Converter {
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
            return ManagementContext.class.isAssignableFrom(type);
        }
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            // write nothing, and always insert the current mgmt context
        }
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return lookupContext.lookupManagementContext();
        }
    }

    /** When reading/writing specs, it checks whether there is a catalog item id set and uses it to load */
    public class SpecConverter extends ReflectionConverter {
        SpecConverter() {
            super(xstream.getMapper(), xstream.getReflectionProvider());
        }
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
            return AbstractBrooklynObjectSpec.class.isAssignableFrom(type);
        }
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (source == null) return;
            AbstractBrooklynObjectSpec<?, ?> spec = (AbstractBrooklynObjectSpec<?, ?>) source;
            String catalogItemId = spec.getCatalogItemId();
            if (Strings.isNonBlank(catalogItemId)) {
                // write this field first, so we can peek at it when we read
                writer.startNode("catalogItemId");
                writer.setValue(catalogItemId);
                writer.endNode();
                
                // we're going to write the catalogItemId field twice :( but that's okay.
                // better solution would be to have mark/reset on reader so we can peek for such a field;
                // see comment below
                super.marshal(source, writer, context);
            } else {
                super.marshal(source, writer, context);
            }
        }
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            String catalogItemId = null;
            instantiateNewInstanceSettingCache(reader, context);
            
            if (reader instanceof PathTrackingReader) {
                // have to assume this is first; there is no mark/reset support on these readers
                // (if there were then it would be easier, we could just look for that child anywhere,
                // and not need a custom writer!)
                if ("catalogItemId".equals( ((PathTrackingReader)reader).peekNextChild() )) {
                    // cache the instance
                    
                    reader.moveDown();
                    catalogItemId = reader.getValue();
                    reader.moveUp();
                }
            }
            boolean customLoaderSet = false;
            try {
                if (Strings.isNonBlank(catalogItemId)) {
                    if (lookupContext==null) throw new NullPointerException("lookupContext required to load catalog item "+catalogItemId);
                    CatalogItem<?, ?> cat = CatalogUtils.getCatalogItemOptionalVersion(lookupContext.lookupManagementContext(), catalogItemId);
                    if (cat==null) throw new NoSuchElementException("catalog item: "+catalogItemId);
                    BrooklynClassLoadingContext clcNew = CatalogUtils.newClassLoadingContext(lookupContext.lookupManagementContext(), cat);
                    pushXstreamCustomClassLoader(clcNew);
                    customLoaderSet = true;
                }
                
                AbstractBrooklynObjectSpec<?, ?> result = (AbstractBrooklynObjectSpec<?, ?>) super.unmarshal(reader, context);
                // we wrote it twice so this shouldn't be necessary; but if we fix it so we only write once, we'd need this
                result.catalogItemId(catalogItemId);
                return result;
            } finally {
                instance = null;
                if (customLoaderSet) {
                    popXstreamCustomClassLoader();
                }
            }
        }

        Object instance;
        
        @Override
        protected Object instantiateNewInstance(HierarchicalStreamReader reader, UnmarshallingContext context) {
            // the super calls getAttribute which requires that we have not yet done moveDown,
            // so we do this earlier and cache it for when we call super.unmarshal
            if (instance==null)
                throw new IllegalStateException("Instance should be created and cached");
            return instance;
        }
        protected void instantiateNewInstanceSettingCache(HierarchicalStreamReader reader, UnmarshallingContext context) {
            instance = super.instantiateNewInstance(reader, context);
        }
    }
    
    Stack<BrooklynClassLoadingContext> contexts = new Stack<BrooklynClassLoadingContext>();
    Stack<ClassLoader> cls = new Stack<ClassLoader>();
    AtomicReference<Thread> xstreamLockOwner = new AtomicReference<Thread>();
    int lockCount;
    
    /** Must be accompanied by a corresponding {@link #popXstreamCustomClassLoader()} when finished. */
    @SuppressWarnings("deprecation")
    protected void pushXstreamCustomClassLoader(BrooklynClassLoadingContext clcNew) {
        acquireXstreamLock();
        BrooklynClassLoadingContext oldClc;
        if (!contexts.isEmpty()) {
            oldClc = contexts.peek();
        } else {
            // TODO XmlMementoSerializer should take a BCLC instead of a CL
            oldClc = JavaBrooklynClassLoadingContext.create(lookupContext.lookupManagementContext(), xstream.getClassLoader());
        }
        BrooklynClassLoadingContextSequential clcMerged = new BrooklynClassLoadingContextSequential(lookupContext.lookupManagementContext(),
            oldClc, clcNew);
        contexts.push(clcMerged);
        cls.push(xstream.getClassLoader());
        ClassLoader newCL = ClassLoaderFromBrooklynClassLoadingContext.of(clcMerged);
        xstream.setClassLoader(newCL);
    }

    protected void popXstreamCustomClassLoader() {
        synchronized (xstreamLockOwner) {
            releaseXstreamLock();
            xstream.setClassLoader(cls.pop());
            contexts.pop();
        }
    }
    
    protected void acquireXstreamLock() {
        synchronized (xstreamLockOwner) {
            while (true) {
                if (xstreamLockOwner.compareAndSet(null, Thread.currentThread()) || 
                    Thread.currentThread().equals( xstreamLockOwner.get() )) {
                    break;
                }
                try {
                    xstreamLockOwner.wait(1000);
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
            }
            lockCount++;
        }
    }

    protected void releaseXstreamLock() {
        synchronized (xstreamLockOwner) {
            if (lockCount<=0) {
                throw new IllegalStateException("xstream not locked");
            }
            if (--lockCount == 0) {
                if (!xstreamLockOwner.compareAndSet(Thread.currentThread(), null)) {
                    Thread oldOwner = xstreamLockOwner.getAndSet(null);
                    throw new IllegalStateException("xstream was locked by "+oldOwner+" but unlock attempt by "+Thread.currentThread());
                }
                xstreamLockOwner.notifyAll();
            }
        }
    }

}
