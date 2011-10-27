package brooklyn.gemfire.demo;

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

public class HubManager implements GatewayChangeListener, RegionChangeListener {

    private Cache cache;

    public HubManager( Cache cache ) {
        this.cache = cache;
    }

    @Override
    public void gatewayAdded( String id, String endpointId, String host, int port, GatewayQueueAttributes attributes ) throws IOException {
    	DiskStore ds = cache.createDiskStoreFactory().create(computeDiskStoreName(endpointId));
        attributes.setDiskStoreName( ds.getName() );
        
        for(GatewayHub hub :  cache.getGatewayHubs()) {
            stopHub(hub);
            addGateway(hub,id,endpointId,host,port,attributes);
            startHub(hub);
        }
    }
    
    private String computeDiskStoreName(String endpointId) {
        return "overflow-"+endpointId+"-"+new Random().nextInt();
    }

    @Override
    public boolean gatewayRemoved( String id ) throws IOException {
    	boolean removed = false;
    	for( GatewayHub hub :  cache.getGatewayHubs() ) {
    		for( Gateway gateway: hub.getGateways()) {
    			if (id.equals(gateway.getId())) {
    				stopHub(hub);
    				hub.removeGateway(id);
    				startHub(hub);
    				removed = true;
    			}
    		}
    		for ( Gateway gateway: hub.getGateways() ) gateway.start();
    	}
    	return removed;
    }

    @Override
    public boolean regionAdded(String path, boolean recurse) throws IOException {
    	Iterable<String> nameparts = Splitter.on(Region.SEPARATOR)
        	.trimResults()
        	.omitEmptyStrings()
        	.split(path);
    	
    	StringBuffer pathBuffer = new StringBuffer();
    	Region<?,?> region;
    	RegionFactory<Object, Object> regionFactory = cache.createRegionFactory(RegionShortcut.REPLICATE).setEnableGateway(new Boolean(true));
    	boolean create = false;
    	
    	if (Iterables.size(nameparts) == 0) {return false;} //throw?
    	
    	String regionName = Iterables.get(nameparts, 0);
    	pathBuffer.append(Region.SEPARATOR).append(regionName);
    	region = cache.getRegion(pathBuffer.toString());
    	
    	if (region == null) {
    		if (Iterables.size(nameparts) > 1 && recurse) {
    			create = recurse;
    		} else {
    			return false;
    		}
    	} 
    	
    	region = regionFactory.create(regionName);
    	
    	for(int i = 1; i < Iterables.size(nameparts); i++) {
    		regionName = Iterables.get(nameparts, i);
    		pathBuffer.append(Region.SEPARATOR).append(regionName);
        	
        	if (create) { // short-circuit to avoid repeated getRegion checks
    			region = region.createSubregion(regionName, region.getAttributes());
    			continue;
    		}
        	
        	if (cache.getRegion(pathBuffer.toString()) == null && (i == Iterables.size(nameparts)-1 || recurse)) {
        		create = recurse;
        		region = region.createSubregion(regionName, region.getAttributes());
        	} else {
        		return false;
        	}
    	}
    	
    	return true;
    }
    	
    @Override
    public boolean regionRemoved(String name) throws IOException {
    	Region<?, ?> r = cache.getRegion(name);
    	if(r != null) {
    		r.localDestroyRegion();
    		return true;
    	}
    	
    	return false;
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
    		if (parent == null) return "";
    		else return parent.path()+Region.SEPARATOR+name;
    	}
    }
    
    @Override
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
    
    @Override
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

    private Gateway addGateway( GatewayHub hub,
                                String id,String endpointId, String host, int port, GatewayQueueAttributes attributes ) {
        Gateway gateway = hub.addGateway(id);
        try {
            gateway.addEndpoint(endpointId,host,port);
            gateway.setQueueAttributes(attributes);
        } catch(GatewayException ge) {
            hub.removeGateway(id);
            throw ge;
        }

        return gateway;
    }

    /**
     * Stops the gateways attached to a hub, then stops the hub
     * @param hub the hub to stop
     */
    private void stopHub( GatewayHub hub ) {
        for ( Gateway gateway : hub.getGateways() ) gateway.stop();
        hub.stop();
    }

    /**
     * Starts the hub and its gateways
     * @param hub the hub to start
     * @throws IOException if there is a problem starting the hub
     */
    private void startHub( GatewayHub hub ) throws IOException {
       hub.start();
       for ( Gateway gateway : hub.getGateways() ) gateway.start();
    }

}
