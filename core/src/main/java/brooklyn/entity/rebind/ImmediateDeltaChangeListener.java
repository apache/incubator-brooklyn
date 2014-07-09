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
package brooklyn.entity.rebind;

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationInternal;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.LocationMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

import com.google.common.collect.Maps;

/**
 * Persists changes immediately. This can cause massive CPU load if entities etc are changing frequently
 * (for any serializing / file-based persister implementation).
 * 
 * @author aled
 * 
 * @deprecated since 0.7; unused code
 */
@Deprecated
public class ImmediateDeltaChangeListener implements ChangeListener {

    private final BrooklynMementoPersister persister;
    private final PersistenceExceptionHandler exceptionHandler;
    
    private volatile boolean running = true;

    public ImmediateDeltaChangeListener(BrooklynMementoPersister persister) {
        this.persister = persister;
        exceptionHandler = PersistenceExceptionHandlerImpl.builder()
                .build();
    }
    
    @Override
    public void onManaged(Entity entity) {
        onChanged(entity);
    }

    @Override
    public void onManaged(Location location) {
        onChanged(location);
    }
    
    @Override
    public void onManaged(Policy policy) {
        onChanged(policy);
    }
    
    @Override
    public void onManaged(Enricher enricher) {
        onChanged(enricher);
    }
    
    @Override
    public void onChanged(Entity entity) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.entities.add(((EntityInternal)entity).getRebindSupport().getMemento());

            // FIXME How to let the policy/location tell us about changes?
            // Don't do this every time!
            Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
            for (Location location : entity.getLocations()) {
                if (!locations.containsKey(location.getId())) {
                    Collection<Location> locsInHierachy = TreeUtils.findLocationsInHierarchy(location);

                    /*
                     * Need to guarantee "happens before", with any thread that has written 
                     * fields of these locations. In particular, saw failures where SshMachineLocation
                     * had null address field. Our hypothesis is that the location had its fields set,
                     * and then set its parent (which goes through a synchronized in AbstractLocation.addChild),
                     * but that this memento-generating code did not go through any synchronization or volatiles.
                     */
                    synchronized (new Object()) {}
                    
                    for (Location locInHierarchy : locsInHierachy) {
                        locations.put(locInHierarchy.getId(), ((LocationInternal)locInHierarchy).getRebindSupport().getMemento());
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

            /*
             * Make the writes to the mementos visible to other threads.
             */
            synchronized (new Object()) {}

            persister.delta(delta, exceptionHandler);
        }
    }
    
    @Override
    public void onUnmanaged(Entity entity) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.removedEntityIds.add(entity.getId());
            persister.delta(delta, exceptionHandler);
        }
    }

    @Override
    public void onUnmanaged(Location location) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.removedLocationIds.add(location.getId());
            persister.delta(delta, exceptionHandler);
        }
    }

    @Override
    public void onUnmanaged(Policy policy) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.removedPolicyIds.add(policy.getId());
            persister.delta(delta, exceptionHandler);
        }
    }

    @Override
    public void onUnmanaged(Enricher enricher) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.removedEnricherIds.add(enricher.getId());
            persister.delta(delta, exceptionHandler);
        }
    }

    @Override
    public void onChanged(Location location) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.locations.add(((LocationInternal)location).getRebindSupport().getMemento());
            persister.delta(delta, exceptionHandler);
        }
    }
    
    @Override
    public void onChanged(Policy policy) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.policies.add(policy.getRebindSupport().getMemento());
            persister.delta(delta, exceptionHandler);
        }
    }
    
    @Override
    public void onChanged(Enricher enricher) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            delta.enrichers.add(enricher.getRebindSupport().getMemento());
            persister.delta(delta, exceptionHandler);
        }
    }
}
