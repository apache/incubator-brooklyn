package brooklyn.entity.group;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.CustomAggregatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * When a dynamic fabric is started, it starts an entity in each of its locations. 
 * This entity will be the parent of each of the started entities. 
 */
public class DynamicFabricImpl extends AbstractGroupImpl implements DynamicFabric {
    private static final Logger logger = LoggerFactory.getLogger(DynamicFabricImpl.class);

    private CustomAggregatingEnricher fabricSizeEnricher;

    public DynamicFabricImpl() {
    }

    @Override
    public void init() {
        super.init();
        
        fabricSizeEnricher = CustomAggregatingEnricher.newSummingEnricher(Changeable.GROUP_SIZE, FABRIC_SIZE);
        addEnricher(fabricSizeEnricher);
        
        setAttribute(SERVICE_UP, false);
    }
    
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC);
    }
    
    protected EntityFactory<?> getFactory() {
        return getConfig(FACTORY);
    }
    
    protected String getDisplayNamePrefix() {
        return getConfig(DISPLAY_NAME_PREFIX);
    }
    
    protected String getDisplayNameSuffix() {
        return getConfig(DISPLAY_NAME_SUFFIX);
    }
    
    @Override
    public void setMemberSpec(EntitySpec<?> memberSpec) {
        setConfigEvenIfOwned(MEMBER_SPEC, memberSpec);
    }
    
    @Override
    public void setFactory(EntityFactory<?> factory) {
        setConfigEvenIfOwned(FACTORY, factory);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull(locations, "locations must be supplied");
        Preconditions.checkArgument(locations.size() >= 1, "One or more location must be supplied");
        addLocations(locations);
        
        Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
        for (Location it : locations) {
            Entity e = addCluster(it);
            // FIXME: this is a quick workaround to ensure that the location is available to any membership change
            //        listeners (notably AbstractDeoDnsService). A more robust mechanism is required; see ENGR-????
            //        for ideas and discussion.
            ((EntityInternal)e).addLocations(Arrays.asList(it));
            if (e instanceof Startable) {
                Task task = e.invoke(Startable.START, ImmutableMap.of("locations", ImmutableList.of(it)));
                tasks.put(e, task);
            }
        }
        waitForTasksOnStart(tasks);
        setAttribute(SERVICE_UP, true);
    }

    protected void waitForTasksOnStart(Map<Entity, Task<?>> tasks) {
        // TODO Could do best-effort for waiting for remaining tasks, rather than failing on first?

        for (Map.Entry<Entity, Task<?>> entry: tasks.entrySet()) {
            try {
                entry.getValue().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Throwables.propagate(e);
            } catch (ExecutionException ee) {
                throw Throwables.propagate(ee.getCause());
            }
        }
    }
    
    @Override
    public void stop() {
        Iterable<Entity> stoppableChildren = Iterables.filter(getChildren(), Predicates.instanceOf(Startable.class));
        Task invoke = Entities.invokeEffector(this, stoppableChildren, Startable.STOP);
        try {
	        if (invoke != null) invoke.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        } catch (ExecutionException ee) {
            throw Throwables.propagate(ee.getCause());
        }

        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer getFabricSize() {
        int result = 0;
        for (Entity child : getChildren()) {
            result  += GroovyJavaMethods.<Integer>elvis(child.getAttribute(Changeable.GROUP_SIZE), 0);
        }
        return result;
    }
    
    @Override
    public boolean removeChild(Entity child) {
        boolean changed = super.removeChild(child);
        if (changed) {
            removeMember(child);
        }
        return changed;
    }
    
    protected Map getCustomChildFlags() {
        Map result = getConfig(CUSTOM_CHILD_FLAGS);
        return (result == null) ? ImmutableMap.of() : result;
    }
    
    protected Entity addCluster(Location location) {
        String locationName = elvis(location.getLocationProperty("displayName"), location.getDisplayName(), null);
        Map creation = Maps.newLinkedHashMap();
        creation.putAll(getCustomChildFlags());
        if (truth(getDisplayNamePrefix()) || truth(getDisplayNameSuffix())) {
            String displayName = "" + elvis(getDisplayNamePrefix(), "") + elvis(locationName, "unnamed") + elvis(getDisplayNameSuffix(),"");
            creation.put("displayName", displayName);
        }
        logger.info("Creating entity in fabric {} at {}{}", new Object[] {this, location, 
                (creation!=null && !creation.isEmpty() ? ", properties "+creation : "") });

        Entity entity = createCluster(location, creation);
        
        if (locationName != null) {
            if (entity.getDisplayName()==null)
                ((EntityLocal)entity).setDisplayName(entity.getEntityType().getSimpleName() +" ("+locationName+")");
            else if (!entity.getDisplayName().contains(locationName)) 
                ((EntityLocal)entity).setDisplayName(entity.getDisplayName() +" ("+locationName+")");
        }
        if (entity.getParent()==null) entity.setParent(this);
        Entities.manage(entity);
        addMember(entity);
        
        fabricSizeEnricher.addProducer(entity);

        return entity;
    }
    
    protected Entity createCluster(Location location, Map flags) {
        EntitySpec<?> memberSpec = getMemberSpec();
        if (memberSpec != null) {
            return addChild(EntitySpec.create(memberSpec).configure(flags));
        }
        
        EntityFactory<?> factory = getFactory();
        if (factory == null) { 
            throw new IllegalStateException("No member spec nor entity factory supplied for dynamic fabric "+this);
        }
        EntityFactory<?> factoryToUse = (factory instanceof EntityFactoryForLocation) ? ((EntityFactoryForLocation)factory).newFactoryForLocation(location) : factory;
        Entity entity = factoryToUse.newEntity(flags, this);
        if (entity==null) 
            throw new IllegalStateException("EntityFactory factory routine returned null entity, in "+this);
        
        return entity;
    }
    
}
