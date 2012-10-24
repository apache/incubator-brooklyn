package brooklyn.entity.rebind;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.LocationMemento;
import brooklyn.policy.Policy;

import com.google.common.collect.Maps;

/**
 * Persists changes immediately. This can cause massive CPU load if entities etc are changing frequently
 * (for any serializing / file-based persister implementation).
 * 
 * @author aled
 */
public class ImmediateDeltaChangeListener implements ChangeListener {

    private final BrooklynMementoPersister persister;
    
    private volatile boolean running = true;

    public ImmediateDeltaChangeListener(BrooklynMementoPersister persister) {
        this.persister = persister;
    }
    
    @Override
    public void onManaged(Entity entity) {
        if (running && persister != null) {
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
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.entities.add(entity.getRebindSupport().getMemento());

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
//            List<PolicyMemento> policies = Lists.newArrayList();
//            for (Policy policy : entity.getPolicies()) {
//                policies.add(policy.getRebindSupport().getMemento());
//            }
//            delta.policies = policies;

            persister.delta(delta);
        }
    }
    
    @Override
    public void onUnmanaged(Entity entity) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.removedEntityIds.add(entity.getId());
            persister.delta(delta);
        }
    }

    @Override
    public void onUnmanaged(Location location) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.removedLocationIds.add(location.getId());
            persister.delta(delta);
        }
    }

    @Override
    public void onChanged(Location location) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.locations.add(location.getRebindSupport().getMemento());
            persister.delta(delta);
        }
    }
    
    @Override
    public void onChanged(Policy policy) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.policies.add(policy.getRebindSupport().getMemento());
            persister.delta(delta);
        }
    }
}
