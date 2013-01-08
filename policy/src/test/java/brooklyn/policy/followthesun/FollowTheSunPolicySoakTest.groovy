package brooklyn.policy.followthesun

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.policy.loadbalancing.MockContainerEntity
import brooklyn.policy.loadbalancing.MockItemEntity
import brooklyn.policy.loadbalancing.Movable
import brooklyn.test.entity.TestApplication

import com.google.common.collect.HashMultimap
import com.google.common.collect.Iterables
import com.google.common.collect.Multimap

public class FollowTheSunPolicySoakTest extends AbstractFollowTheSunPolicyTest {

    protected static final Logger LOG = LoggerFactory.getLogger(FollowTheSunPolicySoakTest.class)
    
    private static final long TIMEOUT_MS = 10*1000;
    
    @Test
    public void testFollowTheSunQuickTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 1
        config.numLocations=3
        config.numContainersPerLocation = 5
        config.numLockedItemsPerLocation = 2
        config.numMovableItems = 10
    
        runFollowTheSunSoakTest(config)
    }
    
    @Test
    public void testLoadBalancingManyItemsQuickTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 1
        config.numLocations=3
        config.numContainersPerLocation = 5
        config.numLockedItemsPerLocation = 2
        config.numMovableItems = 100
        config.numContainerStopsPerCycle = 1
        config.numItemStopsPerCycle = 1
    
        runFollowTheSunSoakTest(config)
    }
    
    @Test(groups=["Integration","Acceptance"]) // integration group, because it's slow to run many cycles
    public void testLoadBalancingSoakTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 100
        config.numLocations=3
        config.numContainersPerLocation = 5
        config.numLockedItemsPerLocation = 2
        config.numMovableItems = 10
    
        runFollowTheSunSoakTest(config)
    }

    @Test(groups=["Integration","Acceptance"]) // integration group, because it's slow to run many cycles
    public void testLoadBalancingManyItemsSoakTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 100
        config.numLocations=3
        config.numContainersPerLocation = 5
        config.numLockedItemsPerLocation = 2
        config.numMovableItems = 100
        config.numContainerStopsPerCycle = 3
        config.numItemStopsPerCycle = 10
        
        runFollowTheSunSoakTest(config)
    }

    @Test(groups=["Integration","Acceptance"]) // integration group, because it's slow to run many cycles
    public void testLoadBalancingManyManyItemsTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 1
        config.numLocations=10
        config.numContainersPerLocation = 5
        config.numLockedItemsPerLocation = 100
        config.numMovableItems = 1000
        config.numContainerStopsPerCycle = 0
        config.numItemStopsPerCycle = 0
        config.timeout_ms = 30*1000
        config.verbose = false
        
        runFollowTheSunSoakTest(config)
    }
    
    private void runFollowTheSunSoakTest(RunConfig config) {
        int numCycles = config.numCycles
        int numLocations = config.numLocations
        int numContainersPerLocation = config.numContainersPerLocation
        int numLockedItemsPerLocation = config.numLockedItemsPerLocation
        int numMovableItems = config.numMovableItems
        
        int numContainerStopsPerCycle = config.numContainerStopsPerCycle
        int numItemStopsPerCycle = config.numItemStopsPerCycle
        int timeout_ms = config.timeout_ms
        boolean verbose = config.verbose
        
        MockItemEntity.totalMoveCount.set(0)
        
        List<Location> locations = new ArrayList<Location>()
        Multimap<Location,MockContainerEntity> containers = new HashMultimap<Location,MockContainerEntity>()
        Multimap<Location,MockItemEntity> lockedItems = new HashMultimap<Location,MockItemEntity>()
        List<MockItemEntity> movableItems = new ArrayList<MockItemEntity>()
        
        for (int i in 1..numLocations) {
            String locName = "loc"+i
            Location loc = new SimulatedLocation(name:locName)
            locations.add(loc)
            
            for (int j in 1..numContainersPerLocation) {
                MockContainerEntity container = newContainer(app, loc, "container-$locName-$j")
                containers.put(loc, container)
            }
            for (int j in 1..numLockedItemsPerLocation) {
                MockContainerEntity container = Iterables.get(containers.get(loc), j%numContainersPerLocation);
                MockItemEntity item = newLockedItem(app, container, "item-locked-$locName-$j")
                lockedItems.put(loc, item)
            }
        }
        
        for (int i in 1..numMovableItems) {
            MockContainerEntity container = Iterables.get(containers.values(), i%containers.size());
            MockItemEntity item = newItem(app, container, "item-movable$i")
            movableItems.add(item)
        }

        for (int i in 1..numCycles) {
            LOG.info(FollowTheSunPolicySoakTest.class.getSimpleName()+": cycle $i")
            
            // Stop movable items, and start others
            for (j in 1..numItemStopsPerCycle) {
                int itemIndex = random.nextInt(numMovableItems)
                MockItemEntity itemToStop = movableItems.get(itemIndex)
                itemToStop.stop()
                LOG.debug("Unmanaging item {}", itemToStop)
                Entities.unmanage(itemToStop)
                movableItems.set(itemIndex, newItem(app, Iterables.get(containers.values(), 0), "item-movable$itemIndex"))
            }

            // Choose a location to be busiest
            int locIndex = random.nextInt(numLocations)
            Location busiestLocation = locations.get(locIndex)
            
            // Repartition the load across the items
            for (int j in 0..(numMovableItems-1)) {
                MockItemEntity item = movableItems.get(j)
                Map<MockItemEntity, Double> workrates = [:]
                
                for (Map.Entry<Location,MockItemEntity> entry : lockedItems.entries()) {
                    Location location = entry.getKey()
                    MockItemEntity source = entry.getValue()
                    double baseWorkrate = (location == busiestLocation ? 1000 : 100);
                    double jitter = 10
                    double jitteredWorkrate = Math.max(0, baseWorkrate + (random.nextDouble()*jitter*2 - jitter))
                    workrates.put(source, jitteredWorkrate)
                }
                item.setAttribute(TEST_METRIC, workrates)
            }

            // Stop containers, and start others
            // This offloads the "immovable" items to other containers in the same location!
            for (j in 1..numContainerStopsPerCycle) {
                int containerIndex = random.nextInt(containers.size())
                MockContainerEntity containerToStop = Iterables.get(containers.values(), containerIndex)
                Location location = Iterables.get(containerToStop.getLocations(), 0)
                MockContainerEntity otherContainerInLocation = containers.get(location).find { it != containerToStop }
                containerToStop.offloadAndStop(otherContainerInLocation)
                LOG.debug("Unmanaging container {}", containerToStop)
                app.managementContext.unmanage(containerToStop)
                containers.remove(location, containerToStop)
                
                MockContainerEntity containerToAdd = newContainer(app, location, "container-${location.name}-new.$i.$j")
                containers.put(location, containerToAdd)
            }

            // Assert that the items all end up in the location with maximum load-generation
            executeUntilSucceeds(timeout:timeout_ms) {
                List<Location> itemLocs = movableItems.collect { 
                    Collection<Location> locs = it.getAttribute(Movable.CONTAINER)?.getLocations()
                    return (locs != null && locs.size() > 0) ? Iterables.get(locs, 0) : null
                }
                List<String> itemLocNames = itemLocs.collect { it?.getName() }
                String errMsg
                if (verbose) {
                    errMsg = verboseDumpToString()+"; itemLocs=$itemLocNames"
                } else {
                    Collection<String> locNamesInUse = new LinkedHashSet<String>(itemLocNames)
                    errMsg = "locsInUse=$locNamesInUse; totalMoves=${MockItemEntity.totalMoveCount}"
                }
                
                assertEquals(itemLocs, Collections.nCopies(movableItems.size(), busiestLocation), errMsg)
            }
        }
    }
    
    private static class RunConfig {
        int numCycles = 1
        int numLocations = 3
        int numContainersPerLocation = 5
        int numLockedItemsPerLocation = 5
        int numMovableItems = 5
        int numContainerStopsPerCycle = 0
        int numItemStopsPerCycle = 0
        int timeout_ms = TIMEOUT_MS
        boolean verbose = true
    }
}
