package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.trait.Balanceable
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.Dmn1PlumberInternalAsync
import com.google.gson.Gson

public class MediatorGroup extends MontereyTypedGroup implements Balanceable {

    static MediatorGroup newSingleLocationInstance(MontereyNetworkConnectionDetails connectionDetails, Location loc) {
        return new MediatorGroup(connectionDetails, Collections.singleton(loc), { it.contains(loc) } );
    }
    
    static MediatorGroup newAllLocationsInstance(MontereyNetworkConnectionDetails connectionDetails, Collection<Location> locs) {
        return new MediatorGroup(connectionDetails, locs, { true } );
    }
    
    MediatorGroup(MontereyNetworkConnectionDetails connectionDetails, Collection<Location> locs, Closure locFilter) {
        super(connectionDetails, Dmn1NodeType.M, locs, locFilter)
    }

    public void moveSegment(String segmentId, MediatorNode destination) {
        Dmn1PlumberInternalAsync plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        plumber.migrateSegment(segmentId, destination.getNodeId());
    }
}
