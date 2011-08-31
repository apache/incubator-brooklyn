package com.cloudsoftcorp.monterey.brooklyn.entity

import java.net.URL;
import java.util.Collection

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.DynamicGroup
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.google.gson.Gson


public class MontereyNodeGroup extends DynamicGroup {

    // FIXME Implement Startable
    
    private static final Logger LOG = LoggerFactory.getLogger(MediatorNode.class)

    public static final BasicAttributeSensor<Dmn1NodeType> NODE_TYPE = [ Dmn1NodeType.class, "monterey.node-type", "Node type" ]
    
    final Dmn1NodeType nodeType;
    
    protected final MontereyProvisioner montereyProvisioner;
    protected final Gson gson;
    protected final MontereyNetworkConnectionDetails connectionDetails;
    
    static Closure closureForMatchingLocation(final Location desired) {
        return { Collection<Location> locs ->
            boolean result = false
            locs.each {
                Location loc = it
                while (loc != null) {
                    if (desired.equals(loc)) {
                        result = true
                        break;
                    }
                    loc = loc.getParentLocation()
                }
            }
            return result
        }
    }
    
    static MontereyNodeGroup newSingleLocationInstance(Map flags=[:], MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Dmn1NodeType nodeType, final Location loc) {
        return new MontereyNodeGroup(flags, connectionDetails, montereyProvisioner, nodeType, Collections.singleton(loc), closureForMatchingLocation(loc) );
    }
    
    static MontereyNodeGroup newAllLocationsInstance(Map flags=[:], MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Dmn1NodeType nodeType, Collection<Location> locs) {
        return new MontereyNodeGroup(flags, connectionDetails, montereyProvisioner, nodeType, locs, { true } );
    }
    
    MontereyNodeGroup(Map flags=[:], MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Dmn1NodeType nodeType, Collection<Location> locs, Closure locFilter) {
        super(flags)
        this.connectionDetails = connectionDetails;
        this.montereyProvisioner = montereyProvisioner;
        this.nodeType = nodeType;
        this.locations.addAll(locs);
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();

        setEntityFilter { Entity e -> 
            e instanceof AbstractMontereyNode && nodeType.equals(((AbstractMontereyNode)e).getNodeType()) && 
                    locFilter(((AbstractMontereyNode)e).getLocations())
        }
        
        LOG.info("Group created for node type $nodeType in locations $locations");        
    }

    // Override to intercept when we are able to set attributes...
    @Override    
    public void setOwner(Entity owner) {
        super.setOwner(owner)
        
        setAttribute(NODE_TYPE, nodeType)
    }
    
    void refreshLocations(Collection<Location> locs) {
        this.locations.clear();
        this.locations.addAll(locs);
    }
    
    void dispose() {
    }
    
    public void provisionNodes(int num) {
        Collection<MontereyContainerNode> nodes = montereyProvisioner.requestNodes(locations, num)
        nodes.each {
            it.rollout(nodeType)
        }
    }
    
    public void resize(Integer desiredSize) {
        int currentSize = getExpecteSize()
        LOG.info("Resizing "+nodeType+" group in "+locations+"; from "+currentSize+" to "+desiredSize)
        if (currentSize < desiredSize) {
            provisionNodes(desiredSize-currentSize)
        } else if (currentSize > desiredSize) {
            // TODO sensible decision of which to release?
            int numTorelease = currentSize-desiredSize;
            List<Entity> members = getMembers() as ArrayList
            List<Entity> torelease = getMembers().subList(0, Math.min(numTorelease, members.size()))
            for (Entity member in torelease) {
                montereyProvisioner.releaseNode(member)
            }
        } else {
            // no-op; it's the same size
        }
    }
    
    public int getExpecteSize() {
        // TODO What about nodes that are still being provisioned?
        return getMembers().size()
    }
}
