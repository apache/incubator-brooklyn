package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoPersister.Delta;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.policy.Policy;
import brooklyn.util.MutableMap;
import brooklyn.util.javalang.Reflections;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RebindManagerImpl implements RebindManager {

    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private final ManagementContext managementContext;

    private final ChangeListener changeListener;
    
    private volatile BrooklynMementoPersister persister;

    private volatile boolean running = true;
    
    public RebindManagerImpl(ManagementContext managementContext) {
        this.managementContext = managementContext;
        this.changeListener = new CheckpointingChangeListener();
    }
    
    @Override
    public void setPersister(BrooklynMementoPersister val) {
        if (persister != null && persister != val) {
            throw new IllegalStateException("Dynamically changing persister is not supported: old="+persister+"; new="+val);
        }
        this.persister = checkNotNull(val, "persister");
    }

    @Override
    public BrooklynMementoPersister getPersister() {
        return persister;
    }
    
    @Override
    public void stop() {
        running = false;
        persister.stop();
    }
    
    @Override
    public ChangeListener getChangeListener() {
        return changeListener;
    }
    
    @Override
    public List<Application> rebind(final BrooklynMemento memento) {
        return rebind(memento, getClass().getClassLoader());
    }
    
    @Override
    public List<Application> rebind(final BrooklynMemento memento, ClassLoader classLoader) {
        checkNotNull(memento, "memento");
        checkNotNull(classLoader, "classLoader");
        
        Reflections reflections = new Reflections(classLoader);
        Map<String,Entity> entities = Maps.newLinkedHashMap();
        Map<String,Location> locations = Maps.newLinkedHashMap();
        Map<String,Policy> policies = Maps.newLinkedHashMap();
        
        final RebindContextImpl rebindContext = new RebindContextImpl(classLoader);

        // Instantiate locations
        LOG.info("RebindManager instantiating locations: {}", memento.getLocationIds());
        for (LocationMemento locMemento : memento.getLocationMementos().values()) {
            if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating location {}", locMemento);
            
            Location location = newLocation(locMemento, reflections);
            locations.put(locMemento.getId(), location);
            rebindContext.registerLocation((Location) location);
        }
        
        // Instantiate entities
        LOG.info("RebindManager instantiating entities: {}", memento.getEntityIds());
        for (EntityMemento entityMemento : memento.getEntityMementos().values()) {
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating entity {}", entityMemento);
            
            Entity entity = newEntity(entityMemento, reflections);
            entities.put(entityMemento.getId(), entity);
            rebindContext.registerEntity((Entity) entity);
        }
        
        // Instantiate policies
        LOG.info("RebindManager instantiating policies: {}", memento.getPolicyIds());
        for (PolicyMemento policyMemento : memento.getPolicyMementos().values()) {
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating policy {}", policyMemento);
            
            Policy policy = newPolicy(policyMemento, reflections);
            policies.put(policy.getId(), policy);
            rebindContext.registerPolicy(policy);
        }
        
        // Reconstruct locations
        LOG.info("RebindManager constructing locations");
        for (LocationMemento locMemento : memento.getLocationMementos().values()) {
            Location location = rebindContext.getLocation(locMemento.getId());
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing location {}", locMemento);

            location.getRebindSupport().reconstruct(rebindContext, locMemento);
        }

        // Reconstruct policies
        LOG.info("RebindManager constructing policies");
        for (PolicyMemento policyMemento : memento.getPolicyMementos().values()) {
            Policy policy = rebindContext.getPolicy(policyMemento.getId());
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing policy {}", policyMemento);

            policy.getRebindSupport().reconstruct(rebindContext, policyMemento);
        }

        // Reconstruct entities
        LOG.info("RebindManager constructing entities");
        for (EntityMemento entityMemento : memento.getEntityMementos().values()) {
            Entity entity = rebindContext.getEntity(entityMemento.getId());
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing entity {}", entityMemento);

            entity.getRebindSupport().reconstruct(rebindContext, entityMemento);
        }
        
        // Manage the top-level apps (causing everything under them to become managed)
        LOG.info("RebindManager managing entities");
        for (String appId : memento.getApplicationIds()) {
            managementContext.manage((Application)rebindContext.getEntity(appId));
        }
        
        // Return the top-level applications
        List<Application> apps = Lists.newArrayList();
        for (String appId : memento.getApplicationIds()) {
            apps.add((Application)rebindContext.getEntity(appId));
        }
        
        LOG.info("RebindManager complete; return apps: {}", memento.getApplicationIds());
        return apps;
    }
    
    private Entity newEntity(EntityMemento memento, Reflections reflections) {
        String entityId = memento.getId();
        String entityType = checkNotNull(memento.getType(), "entityType of "+entityId);
        Class<?> entityClazz = reflections.loadClass(entityType);

        Map<String,Object> flags = Maps.newLinkedHashMap();
        flags.put("id", entityId);
        if (AbstractApplication.class.isAssignableFrom(entityClazz)) flags.put("mgmt", managementContext);
        
        // There are several possibilities for the constructor; find one that works.
        // Prefer passing in the flags because required for Application to set the management context
        // TODO Feels very hacky!
        return (Entity) invokeConstructor(reflections, entityClazz, new Object[] {flags}, new Object[] {flags, null}, new Object[] {null}, new Object[0]);
    }
    
    /**
     * Constructs a new location, passing to its constructor the location id and all of memento.getFlags().
     */
    private Location newLocation(LocationMemento memento, Reflections reflections) {
        String locationId = memento.getId();
        String locationType = checkNotNull(memento.getType(), "locationType of "+locationId);
        Class<?> locationClazz = reflections.loadClass(locationType);

        Map<String, Object> flags = MutableMap.<String, Object>builder()
        		.put("id", locationId)
        		.putAll(memento.getFlags())
        		.removeAll(memento.getLocationReferenceFlags())
        		.build();

        return (Location) invokeConstructor(reflections, locationClazz, new Object[] {flags});
    }

    /**
     * Constructs a new location, passing to its constructor the location id and all of memento.getFlags().
     */
    private Policy newPolicy(PolicyMemento memento, Reflections reflections) {
        String id = memento.getId();
        String policyType = checkNotNull(memento.getType(), "policyType of "+id);
        Class<?> policyClazz = reflections.loadClass(policyType);

        Map<String, Object> flags = MutableMap.<String, Object>builder()
                .put("id", id)
                .putAll(memento.getFlags())
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

    private void depthFirst(BrooklynMemento memento, RebindContext rebindContext, LocationVisitor visitor) {
        List<String> orderedIds = depthFirstOrder(memento.getTopLevelLocationIds(), memento.getLocationMementos());

        for (String id : orderedIds) {
            Location loc = rebindContext.getLocation(id);
            LocationMemento locMemento = memento.getLocationMemento(id);
            
            if (loc != null) {
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager {} location {}", visitor.getActivityName(), locMemento);
                visitor.visit(loc, locMemento);
            } else {
                LOG.warn("No location found for id {}; so not {}", id, visitor.getActivityName());
            }
        }
    }

    private void depthFirst(BrooklynMemento memento, RebindContext rebindContext, EntityVisitor visitor) {
        List<String> orderedIds = depthFirstOrder(memento.getApplicationIds(), memento.getEntityMementos());

        for (String id : orderedIds) {
            Entity entity = rebindContext.getEntity(id);
            EntityMemento entityMemento = memento.getEntityMemento(id);
            
            if (entity != null) {
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager {} entity {}", visitor.getActivityName(), entityMemento);
                visitor.visit(entity, entityMemento);
            } else {
                LOG.warn("No entity found for id {}; so not {}", id, visitor.getActivityName());
            }
        }
    }

    private List<String> depthFirstOrder(Collection<String> roots, Map<String, ? extends TreeNode> nodes) {
        Set<String> visited = new HashSet<String>();
        Deque<String> tovisit = new ArrayDeque<String>();
        List<String> result = new ArrayList<String>(nodes.size());
        
        tovisit.addAll(roots);
        
        while (tovisit.size() > 0) {
            String currentId = tovisit.pop();
            TreeNode node = nodes.get(currentId);
            
            if (node == null) {
                LOG.warn("No memento for id {}", currentId);
                
            } else if (visited.add(currentId)) {
                result.add(currentId);
                for (String childId : node.getChildren()) {
                    if (childId == null) {
                        LOG.warn("Null child entity id in entity {}", node);
                    } else if (!nodes.containsKey(childId)) {
                        LOG.warn("Unknown child id {} in {}", childId, node);
                    } else {
                        tovisit.push(childId);
                    }
                }
            } else {
                LOG.warn("Cycle detected in hierarchy (id="+currentId+")");
            }
        }
        
        return result;
    }

    private static abstract class LocationVisitor {
        private final String activityName;

        public LocationVisitor(String activityName) {
            this.activityName = activityName;
        }
        public abstract void visit(Location location, LocationMemento memento);
        
        public String getActivityName() {
            return activityName;
        }
    }
    
    private static abstract class EntityVisitor {
        private final String activityName;

        public EntityVisitor(String activityName) {
            this.activityName = activityName;
        }
        public abstract void visit(Entity entity, EntityMemento memento);
        
        public String getActivityName() {
            return activityName;
        }
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
    
    private class CheckpointingChangeListener implements ChangeListener {

        @Override
        public void onManaged(Entity entity) {
            if (running && persister != null) {
                // TODO Currently, we get an onManaged call for every entity (rather than just the root of a sub-tree)
                // Also, we'll be told about an entity before its children are officially managed.
                // So it's really the same as "changed".
                onChanged(entity);
            }
        }

        @Override
        public void onManaged(Location location) {
            if (running && persister != null) {
                onChanged(location);
            }
        }
        
        @Override
        public void onChanged(Entity entity) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.entities = ImmutableList.of(entity.getRebindSupport().getMemento());

                // FIXME How to let the policy/location tell us about changes?
                // Don't do this every time!
                Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
                for (Location location : entity.getLocations()) {
                    if (!locations.containsKey(location.getId())) {
                        for (Location locationInHierarchy : TreeUtils.findLocationsInHierarchy(location)) {
                            locations.put(locationInHierarchy.getId(), locationInHierarchy.getRebindSupport().getMemento());
                        }
                    }
                }
                delta.locations = locations.values();

                // FIXME Not including policies, because lots of places regiser anonymous inner class policies
                // (e.g. AbstractController registering a AbstractMembershipTrackingPolicy)
                // Also, the entity constructor often re-creates the policy.
                // Also see MementosGenerator.newEntityMementoBuilder()
//                List<PolicyMemento> policies = Lists.newArrayList();
//                for (Policy policy : entity.getPolicies()) {
//                    policies.add(policy.getRebindSupport().getMemento());
//                }
//                delta.policies = policies;

                persister.delta(delta);
            }
        }
        
        @Override
        public void onUnmanaged(Entity entity) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.removedEntityIds = ImmutableList.of(entity.getId());
                persister.delta(delta);
            }
        }

        @Override
        public void onUnmanaged(Location location) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.removedLocationIds = ImmutableList.of(location.getId());
                persister.delta(delta);
            }
        }

        @Override
        public void onChanged(Location location) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.locations = ImmutableList.of(location.getRebindSupport().getMemento());
                persister.delta(delta);
            }
        }
        
        @Override
        public void onChanged(Policy policy) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.policies = ImmutableList.of(policy.getRebindSupport().getMemento());
                persister.delta(delta);
            }
        }
    }
}
