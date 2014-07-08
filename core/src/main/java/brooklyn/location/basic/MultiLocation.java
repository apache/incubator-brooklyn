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
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.cloud.AbstractAvailabilityZoneExtension;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.Strings;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

/** A location which consists of multiple locations stitched together to form availability zones.
 * The first location will be used by default, but if an {@link AvailabilityZoneExtension}-aware entity
 * is used, it may stripe across each of the locations.  See notes at {@link AvailabilityZoneExtension}. */
public class MultiLocation<T extends MachineLocation> extends AbstractLocation implements MachineProvisioningLocation<T> {

    private static final long serialVersionUID = 7993091317970457862L;
    
    @SuppressWarnings("serial")
    @SetFromFlag("subLocations")
    public static final ConfigKey<List<MachineProvisioningLocation<?>>> SUB_LOCATIONS = ConfigKeys.newConfigKey(
            new TypeToken<List<MachineProvisioningLocation<?>>>() {},
            "subLocations", 
            "The sub-machines that this location can delegate to");
    
    @Override
    public void init() {
        super.init();
        List<MachineProvisioningLocation<?>> subLocs = getSubLocations();
        checkState(subLocs.size() >= 1, "sub-locations must not be empty");
        AvailabilityZoneExtension azExtension = new AvailabilityZoneExtensionImpl(getManagementContext(), subLocs);
        addExtension(AvailabilityZoneExtension.class, azExtension);
    }

    public T obtain() throws NoMachinesAvailableException {
        return obtain(MutableMap.of());
    }
    
    /** finds (creates) and returns a {@link MachineLocation}; 
     * this always tries the first sub-location, moving on the second and subsequent if the first throws {@link NoMachinesAvailableException}.
     * (if you want striping across locations, see notes in {@link AvailabilityZoneExtension}.) */
    @SuppressWarnings("unchecked")
    @Override
    public T obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
        List<MachineProvisioningLocation<?>> sublocsList = getSubLocations();
        Iterator<MachineProvisioningLocation<?>> sublocs = sublocsList.iterator();
        List<NoMachinesAvailableException> errors = MutableList.of();
        while (sublocs.hasNext()) {
            try {
                return (T) sublocs.next().obtain(flags);
            } catch (NoMachinesAvailableException e) {
                errors.add(e);
            }
        }
        Exception wrapped;
        String msg;
        if (errors.size()>1) {
            wrapped = new CompoundRuntimeException(errors.size()+" sublocation exceptions, including: "+
                Exceptions.collapseText(errors.get(0)), errors);
            msg = Exceptions.collapseText(wrapped);
        } else if (errors.size()==1) {
            wrapped = errors.get(0);
            msg = wrapped.getMessage();
            if (Strings.isBlank(msg)) msg = wrapped.toString();
        } else {
            msg = "no sub-locations set for this multi-location";
            wrapped = null;
        }
        throw new NoMachinesAvailableException("No machines available in any of the "+sublocsList.size()+" location"+Strings.s(sublocsList.size())+
            " configured here: "+msg, wrapped);
    }

    public List<MachineProvisioningLocation<?>> getSubLocations() {
        return getRequiredConfig(SUB_LOCATIONS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MachineProvisioningLocation<T> newSubLocation(Map<?, ?> newFlags) {
        // TODO shouldn't have to copy config bag as it should be inherited (but currently it is not used inherited everywhere; just most places)
        return getManagementContext().getLocationManager().createLocation(LocationSpec.create(getClass())
                .parent(this)
                .configure(getLocalConfigBag().getAllConfig())  // FIXME Should this just be inherited?
                .configure(newFlags));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void release(T machine) {
        ((MachineProvisioningLocation<T>)machine.getParent()).release(machine);
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.<String,Object>newLinkedHashMap();
    }

    @SuppressWarnings("unchecked")
    protected MachineProvisioningLocation<T> firstSubLoc() {
        return (MachineProvisioningLocation<T>) Iterables.get(getSubLocations(), 0);
    }

    protected <K> K getRequiredConfig(ConfigKey<K> key) {
        return checkNotNull(getConfig(key), key.getName());
    }

    public static class AvailabilityZoneExtensionImpl extends AbstractAvailabilityZoneExtension implements AvailabilityZoneExtension {

        private final List<MachineProvisioningLocation<?>> subLocations;
        
        public AvailabilityZoneExtensionImpl(ManagementContext managementContext, List<MachineProvisioningLocation<?>> subLocations) {
            super(managementContext);
            this.subLocations = ImmutableList.copyOf(subLocations);
        }
        
        @Override
        protected List<Location> doGetAllSubLocations() {
            return ImmutableList.<Location>copyOf(subLocations);
        }
        
        @Override
        protected boolean isNameMatch(Location loc, Predicate<? super String> namePredicate) {
            return namePredicate.apply(loc.getDisplayName());
        }
    }
}
