package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.entity.basic.AbstractEntity

import brooklyn.entity.basic.AbstractEntity

import brooklyn.entity.basic.AbstractEntity

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

import brooklyn.location.Location

import com.cloudsoftcorp.util.Loggers

/**
 * Handles tracking the provisioning (and release) of nodes. It acts as a pool for the spare
 * nodes, returning either an existing spare node or provisioning new ones.
 */
class MontereyProvisioner {

    // FIXME This class should allow policies to control the choice of location etc, and whether we
    // are even allowed to provision another node.

    private static final Logger LOG = Loggers.getLogger(MontereyProvisioner.class)
        
    private final MontereyNetworkConnectionDetails connectionDetails;
    private final MontereyNetwork network;
    private final int maxConcurrentProvisioningsPerLocation
    
    private final ConcurrentHashMap<Location, LocationTracker> locationTrackers = [:]
    
    // FIXME Want to use the ExecutionManager; but want the provisioning to show up as tasks of the 
    // calling entity.
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    /**
     * 
     * @param connectionDetails
     * @param network
     * @param maxConcurrentProvisioningsPerLocation The maximum number of nodes that can be provisioned simultaneously in a given location
     */
    MontereyProvisioner(MontereyNetworkConnectionDetails connectionDetails, MontereyNetwork network, int maxConcurrentProvisioningsPerLocation=Integer.MAX_VALUE) {
        this.connectionDetails = connectionDetails
        this.network = network
        this.maxConcurrentProvisioningsPerLocation = maxConcurrentProvisioningsPerLocation
    }
    
    /**
     * Creates new spare nodes in the given location, returning immediately.
     */
    public Collection<Future<?>> addSpareNodesAsync(Location loc, int num) {
        return getLocationTracker(loc).provisionAndAddSpareNodesAsync(num)
    }

    /**
     * Creates new spare nodes in the given location, blocking until the nodes exist.
     */
    public void addSpareNodes(Location loc, int num) {
        Collection<Future<?>> futures = addSpareNodesAsync(loc, num)

        waitForFutures(futures)
    }
    
    /**
     * Requests the given number of nodes in the given locations, blocking until the
     * nodes exist.
     */
    public Collection<MontereyContainerNode> requestNodes(Location loc, int num) {
        return requestNodes([loc], num)
    }

    /**
     * Requests the given number of nodes in any of the given locations, blocking until the 
     * nodes exist.
     */
    public Collection<MontereyContainerNode> requestNodes(Collection<Location> locs, int num) {
        int remainingRequired = num
        
        Collection<Future<MontereyContainerNode>> existingFutures = []
        for (Location loc : locs) {
            LocationTracker locationTracker = getLocationTracker(loc)
            Collection<Future<MontereyContainerNode>> futures = locationTracker.claimExistingNodes(remainingRequired)
            existingFutures.addAll(futures)
            remainingRequired -= futures.size()
        }
        
        Location loc = locs.iterator().next()
        Collection<Future<MontereyContainerNode>> remainingFutures = getLocationTracker(loc).provisionAndClaimNodesAsync(remainingRequired)
        
        LOG.info("Request for $num nodes in location(s) $locs; had ${existingFutures.size()} in pool and provisioning ${remainingFutures.size()} additional in $loc")
        return waitForFutures(union(existingFutures,remainingFutures))
    }
    
    /**
     * Returns the nodes to the pull; they may be reverted and kept as spares or released if not needed.
     */
    public void releaseNode(MontereyContainerNode node) {
        // TODO Could add node to locationTracker instead...
        LocationTracker locationTracker = getLocationTracker(loc)
        
        network.releaseNode(node)
    }
    
    // TODO This doesn't work with more complex location hierarchies (e.g. if we had eu-west-1, eu-west-1a and eu-west-1b)
    private LocationTracker getLocationTracker(Location loc) {
        synchronized (locationTrackers) {
            for (Location contender : locationTrackers.keySet()) {
                if (contender.containsLocation(loc)) return locationTrackers.get(contender)
            }
            LocationTracker result = new LocationTracker(loc)
            locationTrackers.put(loc, result)
            return result
        }
    }
    
    
    /**
     * Tracks the pool of spare nodes (that either exist or that are being provisioned).
     */
    private class LocationTracker {
        final Location loc;
        final List<MontereyContainerNode> spareNodes = []
        final List<Future<MontereyContainerNode>> pendingSpareNodes = []
        final AtomicInteger inprogressProvisioningsCount = new AtomicInteger(0)
        
        LocationTracker(Location loc) {
            this.loc = loc
        }

        /**
         * Adds a spare-node that is being provisioned (but possibly not finished yet).        
         */
        synchronized void addPending(Future<MontereyContainerNode> future) {
            pendingSpareNodes.add(future)
        }
        
        /**
         * Claims spare-node that already exist, or that are being provisioned.        
         */
        synchronized Collection<Future<MontereyContainerNode>> claimExistingNodes(int num) {
            Collection<Future<MontereyContainerNode>> result = []
            while (num > 0 && spareNodes.size() > 0) {
                result.add(new CompletedFuture(spareNodes.pop()))
                num--
            }
            while (num > 0 && pendingSpareNodes.size() > 0) {
                result.add(pendingSpareNodes.remove(0))
                num--
            }
            return result
        }

        Collection<Future<MontereyContainerNode>> provisionAndClaimNodesAsync(int num) {
            return provisionNodesAsync(num)
        }        

        Collection<Future<?>> provisionAndAddSpareNodesAsync(int num) {
            Collection<Future<MontereyContainerNode>> result = provisionNodesAsync(num)
            for (Future<MontereyContainerNode> future in result) {
                addPending(future)
            }
            return result
        }

        private Collection<Future<MontereyContainerNode>> provisionNodesAsync(int num) {
            LOG.info("Scheduling provisioning of $num spare nodes in location $loc")
            
            Collection<Future<MontereyContainerNode>> result = []
            for (int i = 0; i < num; i++) {
                Future<MontereyContainerNode> future = executor.submit( {
                        synchronized (inprogressProvisioningsCount) {
                            boolean loggedWaiting = false
                            while (inprogressProvisioningsCount.get() >= maxConcurrentProvisioningsPerLocation) {
                                LOG.info("Waiting to provision node in location $loc; "+inprogressProvisioningsCount.get()+" concurrent provisionings in progress")
                                loggedWaiting = true
                                inprogressProvisioningsCount.wait()
                            }
                            if (loggedWaiting) LOG.info("Continuing to provision node in location $loc; "+inprogressProvisioningsCount.get()+" concurrent provisionings in progress")
                            
                            inprogressProvisioningsCount.incrementAndGet()
                        }
                        try {
                            return network.provisionNode(loc)
                            
                        } finally {
                            synchronized (inprogressProvisioningsCount) {
                                inprogressProvisioningsCount.decrementAndGet()
                                inprogressProvisioningsCount.notifyAll()
                            }
                        }
                    } as Callable )
                result.add(future)
            }
            return result
        }
    }
    
    /**
     * Represents an already-completed future, so calling get will immediately return.
     */
    private static class CompletedFuture<T> implements Future<T> {
        private final T result
        CompletedFuture(T result) { this.result = result }
        
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false }
        @Override public boolean isCancelled() { return false }
        @Override public boolean isDone() { return true }
        @Override public T get() { return result }
        @Override public T get(long timeout, TimeUnit unit) { return result }
    }
    
    private static <T> Collection<T> waitForFutures(Collection<Future<T>> futures) {
        Collection<T> result = []
        for (Future<T> future in futures) {
            result.add(future.get())
        }
        return result
    }
    
    private static Collection union(Collection... cols) {
        Collection result = []
        for (Collection col in cols) {
            result.addAll(col)
        }
        return result
    }
}
