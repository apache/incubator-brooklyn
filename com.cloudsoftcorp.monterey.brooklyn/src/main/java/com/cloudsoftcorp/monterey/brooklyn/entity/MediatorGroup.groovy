package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.concurrent.Future

import brooklyn.entity.trait.Balanceable
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.Dmn1PlumberInternalAsync
import com.google.gson.Gson

public class MediatorGroup extends MontereyNodeGroup implements Balanceable {

    static MediatorGroup newSingleLocationInstance(Map flags=[:], MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Location loc) {
        return new MediatorGroup(flags, connectionDetails, montereyProvisioner, Collections.singleton(loc), closureForMatchingLocation(loc) );
    }
    
    static MediatorGroup newAllLocationsInstance(Map flags=[:], MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Collection<Location> locs) {
        return new MediatorGroup(flags, connectionDetails, montereyProvisioner, locs, { true } );
    }
    
    MediatorGroup(Map flags=[:], MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Collection<Location> locs, Closure locFilter) {
        super(flags, connectionDetails, montereyProvisioner, Dmn1NodeType.M, locs, locFilter)
    }

    public void moveSegment(String segmentId, MediatorNode destination) {
        Dmn1PlumberInternalAsync plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        Future<?> future = plumber.migrateSegment(segmentId, destination.getNodeId());
        future.get();
    }
}
