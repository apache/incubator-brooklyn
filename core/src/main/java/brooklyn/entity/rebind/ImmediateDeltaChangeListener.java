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

import brooklyn.basic.BrooklynObject;
import brooklyn.basic.BrooklynObjectInternal;
import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationInternal;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.Memento;
import brooklyn.mementos.PolicyMemento;
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
    public void onManaged(BrooklynObject instance) {
        onChanged(instance);
    }

    @Override
    public void onUnmanaged(BrooklynObject instance) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            if (instance instanceof Entity) {
                delta.removedEntityIds.add(instance.getId());
            } else if (instance instanceof Location) {
                delta.removedLocationIds.add(instance.getId());
            } else if (instance instanceof Policy) {
                delta.removedPolicyIds.add(instance.getId());
            } else if (instance instanceof Enricher) {
                delta.removedEnricherIds.add(instance.getId());
            } else {
                throw new IllegalStateException("Unexpected brooklyn type: "+instance);
            }
            persister.delta(delta, exceptionHandler);
        }
    }

    @Override
    public void onChanged(BrooklynObject instance) {
        if (running && persister != null) {
            PersisterDeltaImpl delta = new PersisterDeltaImpl();
            Memento memento = ((BrooklynObjectInternal)instance).getRebindSupport().getMemento();
            if (instance instanceof Entity) {
                delta.entities.add((EntityMemento) memento);
                addEntityAdjuncts((Entity)instance, delta);
            } else if (instance instanceof Location) {
                delta.locations.add((LocationMemento) memento);
            } else if (instance instanceof Policy) {
                delta.policies.add((PolicyMemento) memento);
            } else if (instance instanceof Enricher) {
                delta.enrichers.add((EnricherMemento) memento);
            } else {
                throw new IllegalStateException("Unexpected brooklyn type: "+instance);
            }
            persister.delta(delta, exceptionHandler);
        }
    }
    
    private void addEntityAdjuncts(Entity entity, PersisterDeltaImpl delta) {
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
    }
}
