package brooklyn.gemfire.api;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DiskStore;
import com.gemstone.gemfire.cache.GatewayException;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionFactory;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.util.Gateway;
import com.gemstone.gemfire.cache.util.GatewayHub;
import com.gemstone.gemfire.cache.util.GatewayQueueAttributes;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class HubManager implements GatewayManager, RegionManager {

    private Cache cache;

    public HubManager(Cache cache) {
        this.cache = cache;
    }

    public void addGateway(String id, String endpointId, String host, int port, GatewayQueueAttributes attributes) throws IOException {
    	DiskStore ds = cache.createDiskStoreFactory().create(computeDiskStoreName(endpointId));
        attributes.setDiskStoreName( ds.getName() );

        for (GatewayHub hub : cache.getGatewayHubs()) {
            stopHub(hub);
            createGatewayAtHub(hub, id, endpointId, host, port, attributes);
            startHub(hub);
        }
    }

    private Gateway createGatewayAtHub(GatewayHub hub, String id, String endpointId,
                                       String host, int port, GatewayQueueAttributes attributes) {
        Gateway gateway = hub.addGateway(id);
        try {
            gateway.addEndpoint(endpointId, host, port);
            gateway.setQueueAttributes(attributes);
        } catch(GatewayException ge) {
            hub.removeGateway(id);
            throw ge;
        }

        return gateway;
    }
    
    private String computeDiskStoreName(String endpointId) {
        return "overflow-" + endpointId + "-" + new Random().nextInt();
    }

    public boolean removeGateway(String id) throws IOException {
    	boolean removed = false;
        for (GatewayHub hub : cache.getGatewayHubs()) {
            for (Gateway gateway : hub.getGateways()) {
                if (id.equals(gateway.getId())) {
                    stopHub(hub);
                    hub.removeGateway(id);
                    startHub(hub);
                    removed = true;
                }
            }
            for (Gateway gateway : hub.getGateways()) gateway.start();
        }
        return removed;
    }

    /**
     * @param path slash-separated name of region
     * @param createIntermediate using example /foo/bar/baz:
     *          when true create /foo/bar/baz if /foo/bar already exists, otherwise do not create anything
     * @return added true if a new region was added to the cache
     * @throws IOException
     */
    public boolean addRegion(String path, boolean createIntermediate) throws IOException {

        // Split path by Region.SEPARATOR (/)
    	Iterable<String> nameparts = Splitter.on(Region.SEPARATOR)
        	.trimResults()
        	.omitEmptyStrings()
        	.split(path);

        if (Iterables.isEmpty(nameparts)) {
            return false;
        }

    	StringBuilder pathBuilder = new StringBuilder();
    	RegionFactory<Object, Object> regionFactory = cache.createRegionFactory(RegionShortcut.REPLICATE).setEnableGateway(true);
    	boolean encounteredNonexistentRegion = false;
    	String regionName = Iterables.get(nameparts, 0);

        // Get first section of region path from cache
    	pathBuilder.append(Region.SEPARATOR).append(regionName);
    	Region<?,?> region = cache.getRegion(pathBuilder.toString());
    	
    	// handle root regions that don't already exist in this cache
    	if (region == null) {
    		encounteredNonexistentRegion = true;
    		if (Iterables.size(nameparts) == 1 || createIntermediate) {
    			region = regionFactory.create(regionName);
    		} else {
    			return false;
    		}
    	}
    	
    	// handle sub-regions
    	for (int i = 1; i < Iterables.size(nameparts); i++) {
    		regionName = Iterables.get(nameparts, i);
    		pathBuilder.append(Region.SEPARATOR).append(regionName);

            // create this region if it doesn't exist because we've already had to create a parent region
            // (encounteredNonexistentRegion) or the region doesn't exist and either we're in the final iteration
            // of the loop or we should create intermediate regions.
            if (encounteredNonexistentRegion ||
                    (!regionExists(pathBuilder.toString()) && (i == Iterables.size(nameparts)-1 || createIntermediate))) {
                encounteredNonexistentRegion = true;
                region = region.createSubregion(regionName, region.getAttributes());
            } else {
                throw new IllegalArgumentException("Error creating subregion");
            }
    	}
    	
    	return encounteredNonexistentRegion;
    }

    private boolean regionExists(String path) {
        return cache.getRegion(path) != null;
    }

    /**
     * Removes the given region from this hub's cache
     */
    public boolean removeRegion(String path) throws IOException {
    	Region<?, ?> r = cache.getRegion(path);
    	if (r != null) {
    		r.localDestroyRegion();
    		return true;
    	} else {
            return false;
        }
    }
    
    public static class RegionNode {
    	public String name;
    	public RegionNode parent;
    	public List<RegionNode> children = new LinkedList<RegionNode>();
    	
    	public RegionNode(String name, RegionNode parent) {
    		if (name == null || "".equals(name)) throw new IllegalArgumentException("Cannot have an empty/null name");
    		if (name.contains(Region.SEPARATOR)) throw new IllegalArgumentException("Name cannot contain seperator "+Region.SEPARATOR);
    		this.name = name;
    		this.parent = parent;
    	}
    	
    	public String path() {
    		return (parent == null) ? "" : parent.path() + Region.SEPARATOR + name;
    	}
    }
    
    public RegionNode regionTree() {
    	RegionNode root = new RegionNode("root", null);
    	RegionNode currentNode = root;
    	Queue<RegionNode> traversalQueue = new LinkedList<RegionNode>();
    	for (Region<?, ?> rootRegion : cache.rootRegions()) {
    		RegionNode newNode = new RegionNode(rootRegion.getName(), currentNode);
    		traversalQueue.add(newNode);
    		root.children.add(newNode);
    	}
    	
    	currentNode = traversalQueue.poll();
    	while (currentNode != null) {
    		Region<?, ?> currentRegion = cache.getRegion(currentNode.path());
    		for (Region<?, ?> subRegion : currentRegion.subregions(false)) {
    			RegionNode newNode = new RegionNode(subRegion.getName(), currentNode);
    			traversalQueue.add(newNode);
    			currentNode.children.add(newNode);
    		}
    		currentNode = traversalQueue.poll();
    	}
    	return root;
    }

    /**
     * @return regions all regions held in this Hub's cache
     */
    public List<String> regionList() {
    	List<String> regions = new LinkedList<String>();
    	for (Region<?, ?> rootRegion : cache.rootRegions()) {
			regions.add(rootRegion.getFullPath());
			for (Region<?, ?> region : rootRegion.subregions(true)) {
				regions.add(region.getFullPath());
			}
		}
    	return regions;
    }

    /**
     * Stops the gateways attached to a hub, then stops the hub
     * @param hub the hub to stop
     */
    private void stopHub(GatewayHub hub) {
        for (Gateway gateway : hub.getGateways()) gateway.stop();
        hub.stop();
    }

    /**
     * Starts the hub and its gateways
     * @param hub the hub to start
     * @throws IOException if there is a problem starting the hub
     */
    private void startHub(GatewayHub hub) throws IOException {
        hub.start();
        for (Gateway gateway : hub.getGateways()) gateway.start();
    }

}
