package brooklyn.internal.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.rebind.ChangeListener;
import brooklyn.entity.rebind.RebindContextImpl;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.Reference;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoPersister.Delta;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.SetFromLiveMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.Reflections;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RebindFromDatagridManagerImpl {

    public static final Logger LOG = LoggerFactory.getLogger(RebindFromDatagridManagerImpl.class);

    private volatile boolean running = true;
    
    private final ManagementContextInternal managementContext;
    private final BrooklynStorage storage;
    private final Map<String,String> entityTypes;
    private final Set<String> applicationIds;
    private final Map<String,String> locationTypes;
    private final Map<String,String> policyTypes;
    private final InternalEntityFactory entityFactory;

    
    public RebindFromDatagridManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
        storage = ((ManagementContextInternal)managementContext).getStorage();
        entityTypes = storage.getMap("entities");
        locationTypes = storage.getMap("locations");
        applicationIds = SetFromLiveMap.create(storage.<String,Boolean>getMap("applications"));
        policyTypes = storage.getMap("policies");
        entityFactory = ((ManagementContextInternal)managementContext).getEntityFactory();
    }

    //@Override
    public void stop() {
        running = false;
    }
    
    //@Override
    @VisibleForTesting
    public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        // FIXME
        if (!running) return;
        //storage.waitForWritesCompleted(timeout, unit);
    }
    
    //@Override
    public List<Application> rebind() {
        return rebind(getClass().getClassLoader());
    }
    
    //@Override
    public List<Application> rebind(ClassLoader classLoader) {
        checkNotNull(classLoader, "classLoader");
        
        Reflections reflections = new Reflections(classLoader);
        Map<String,Entity> entities = Maps.newLinkedHashMap();
        Map<String,Location> locations = Maps.newLinkedHashMap();
        Map<String,Policy> policies = Maps.newLinkedHashMap();
        
        final RebindContextImpl rebindContext = new RebindContextImpl(classLoader);

        // Instantiate locations
        LOG.info("RebindManager instantiating locations: {}", locationTypes.keySet());
        for (Map.Entry<String,String> entry : locationTypes.entrySet()) {
            String id = entry.getKey();
            String type = entry.getValue();
            if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating location {} ({})", id, type);
            
            Location location = newLocation(id, type, reflections);
            locations.put(id, location);
            rebindContext.registerLocation(id, location);
            ((LocalManagementContext)managementContext).prePreManage(location); // FIXME
        }
        
        // Instantiate entities
        LOG.info("RebindManager instantiating entities: {}", entityTypes.keySet());
        for (Map.Entry<String, String> entry : entityTypes.entrySet()) {
            String id = entry.getKey();
            String type = entry.getValue();
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating entity {} ({})", id, type);
            
            Entity entity = newEntity(id, type, reflections);
            entities.put(id, entity);
            rebindContext.registerEntity(id, entity);
            ((LocalManagementContext)managementContext).prePreManage(entity); // FIXME
        }
        
        // Instantiate policies
        LOG.info("RebindManager instantiating policies: {}", policyTypes.keySet());
        for (Map.Entry<String, String> entry : policyTypes.entrySet()) {
            String id = entry.getKey();
            String type = entry.getValue();
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating policy {} ({})", id, type);
            
            Policy policy = newPolicy(id, type, reflections);
            policies.put(id, policy);
            rebindContext.registerPolicy(id, policy);
        }
        
        // Reconstruct locations
        LOG.info("RebindManager reconstructing locations");
        for (String id : locationTypes.keySet()) {
            Location location = rebindContext.getLocation(id);
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing location {}", id);

            ((AbstractLocation)location).setManagementContext(managementContext);

            // FIXME
            //((AbstractLocation)location).rebind();
            //location.getRebindSupport().reconstruct(rebindContext, locMemento);
        }

        // Reconstruct policies
        LOG.info("RebindManager reconstructing policies");
        for (String id : policyTypes.keySet()) {
            Policy policy = rebindContext.getPolicy(id);
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing policy {}", id);

            // FIXME
            //((AbstractPolicy)policy).rebind();
            //policy.getRebindSupport().reconstruct(rebindContext, policyMemento);
        }

        // Reconstruct entities
        LOG.info("RebindManager reconstructing entities");
        for (String id : entityTypes.keySet()) {
            Entity entity = rebindContext.getEntity(id);
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing entity {}", id);

            // FIXME
            ((AbstractEntity)entity).setManagementContext(managementContext);
            //((AbstractEntity)entity).rebind();
            //entity.getRebindSupport().reconstruct(rebindContext, entityMemento);
        }
        
        // Manage the top-level locations (causing everything under them to become managed)
        LOG.info("RebindManager managing locations");
        for (Location location : findTopLevelLocations(locations.values())) {
            Entities.manage(location, managementContext);
        }
        
        // Manage the top-level apps (causing everything under them to become managed)
        LOG.info("RebindManager managing entities");
        List<Application> apps = Lists.newArrayList();
        for (String appId : applicationIds) {
            Application app = (Application) rebindContext.getEntity(appId);
            Entities.startManagement(app, managementContext);
            apps.add(app);
        }
        
        LOG.info("RebindManager complete; return apps: {}", applicationIds);
        return Collections.unmodifiableList(apps);
    }
    
    private List<Location> findTopLevelLocations(Collection<Location> locations) {
        List<Location> result = new ArrayList<Location>();
        for (Location contender : locations) {
            if (contender.getParentLocation() == null) {
                result.add(contender);
            }
        }
        return result;
    }

    private Entity newEntity(String entityId, String entityType, Reflections reflections) {
        Class<? extends Entity> entityClazz = (Class<? extends Entity>) reflections.loadClass(entityType);
        
        Class<?>[] additionalInterfaces = entityClazz.getInterfaces();
        
        try {
            Entity entity = entityFactory.constructEntity(entityClazz);
            
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", entityId), entity);
            ((AbstractEntity)entity).setProxy(entityFactory.createEntityProxy(entity, additionalInterfaces));
            
            return entity;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    /**
     * Constructs a new location, passing to its constructor the location id and all of memento.getFlags().
     */
    private Location newLocation(String locationId, String locationType, Reflections reflections) {
        Class<?> locationClazz = reflections.loadClass(locationType);

        // TODO Move this code inside location?
        // FIXME What about config that refers to other location objects? Those won't have been instantiated yet.
        //       Need to separate constructor from configure
        Reference<String> locationDisplayName = storage.getReference("location-"+locationId+"-displayName");
        Map<String,?> locationConfig = storage.getMap("location-"+locationId+"-config");

        Map<String, Object> flags = MutableMap.<String, Object>builder()
        		.put("id", locationId)
                .putIfNotNull("displayName", locationDisplayName.get())
        		.putAll(locationConfig)
        		.build();

        return (Location) invokeConstructor(reflections, locationClazz, new Object[] {flags});
        // 'used' config keys get marked in BasicLocationRebindSupport
    }

    /**
     * Constructs a new location, passing to its constructor the location id and all of memento.getFlags().
     */
    private Policy newPolicy(String policyId, String policyType, Reflections reflections) {
        Class<?> policyClazz = reflections.loadClass(policyType);

        // TODO Move this code inside location?
        // FIXME What about config that refers to other location/entity/policy objects? Those won't have been instantiated yet.
        //       Need to separate constructor from configure
        Reference<String> policyDisplayName = storage.getReference("policy-"+policyId+"-displayName");
        Map<String,?> policyConfig = storage.getMap("policy-"+policyId+"-config");

        Map<String, Object> flags = MutableMap.<String, Object>builder()
                .put("id", policyId)
                .putIfNotNull("displayName", policyDisplayName.get())
                .putAll(policyConfig)
                .build();

        return (Policy) invokeConstructor(reflections, policyClazz, new Object[] {flags});
    }

    private <T> T invokeConstructor(Reflections reflections, Class<T> clazz, Object[]... possibleArgs) {
        for (Object[] args : possibleArgs) {
            Constructor<T> constructor = Reflections.findCallabaleConstructor(clazz, args);
            if (constructor != null) {
                constructor.setAccessible(true);
                return reflections.loadInstance(constructor, args);
            }
        }
        throw new IllegalStateException("Cannot instantiate instance of type "+clazz+"; expected constructor signature not found");
    }

    private static class DeltaImpl implements Delta {
        Collection<LocationMemento> locations = Collections.emptyList();
        Collection<EntityMemento> entities = Collections.emptyList();
        Collection<PolicyMemento> policies = Collections.emptyList();
        Collection <String> removedLocationIds = Collections.emptyList();
        Collection <String> removedEntityIds = Collections.emptyList();
        Collection <String> removedPolicyIds = Collections.emptyList();
        
        @Override
        public Collection<LocationMemento> locations() {
            return locations;
        }

        @Override
        public Collection<EntityMemento> entities() {
            return entities;
        }

        @Override
        public Collection<PolicyMemento> policies() {
            return policies;
        }

        @Override
        public Collection<String> removedLocationIds() {
            return removedLocationIds;
        }

        @Override
        public Collection<String> removedEntityIds() {
            return removedEntityIds;
        }
        
        @Override
        public Collection<String> removedPolicyIds() {
            return removedPolicyIds;
        }
    }
    
    /**
     * Wraps a ChangeListener, to log and never propagate any exceptions that it throws.
     * 
     * Catches Throwable, because really don't want a problem to propagate up to user code,
     * to cause business-level operations to fail. For example, if there is a linkage error
     * due to some problem in the serialization dependencies then just log it. For things
     * more severe (e.g. OutOfMemoryError) then the catch+log means we'll report that we
     * failed to persist, and we'd expect other threads to throw the OutOfMemoryError so
     * we shouldn't lose anything.
     */
    private static class SafeChangeListener implements ChangeListener {
        private final ChangeListener delegate;
        
        public SafeChangeListener(ChangeListener delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void onManaged(Entity entity) {
            try {
                delegate.onManaged(entity);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onManaged("+entity+"); continuing.", t);
            }
        }

        @Override
        public void onManaged(Location location) {
            try {
                delegate.onManaged(location);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onManaged("+location+"); continuing.", t);
            }
        }
        
        @Override
        public void onChanged(Entity entity) {
            try {
                delegate.onChanged(entity);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+entity+"); continuing.", t);
            }
        }
        
        @Override
        public void onUnmanaged(Entity entity) {
            try {
                delegate.onUnmanaged(entity);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onUnmanaged("+entity+"); continuing.", t);
            }
        }

        @Override
        public void onUnmanaged(Location location) {
            try {
                delegate.onUnmanaged(location);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onUnmanaged("+location+"); continuing.", t);
            }
        }

        @Override
        public void onChanged(Location location) {
            try {
                delegate.onChanged(location);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+location+"); continuing.", t);
            }
        }
        
        @Override
        public void onChanged(Policy policy) {
            try {
                delegate.onChanged(policy);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+policy+"); continuing.", t);
            }
        }
    }
}
