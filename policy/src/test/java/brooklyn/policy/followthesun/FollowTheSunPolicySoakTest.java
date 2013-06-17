package brooklyn.policy.followthesun;

import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.loadbalancing.BalanceableContainer;
import brooklyn.policy.loadbalancing.MockContainerEntity;
import brooklyn.policy.loadbalancing.MockItemEntity;
import brooklyn.policy.loadbalancing.MockItemEntityImpl;
import brooklyn.policy.loadbalancing.Movable;
import brooklyn.test.Asserts;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public class FollowTheSunPolicySoakTest extends AbstractFollowTheSunPolicyTest {

    protected static final Logger LOG = LoggerFactory.getLogger(FollowTheSunPolicySoakTest.class);
    
    private static final long TIMEOUT_MS = 10*1000;
    
    @Test
    public void testFollowTheSunQuickTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 1;
        config.numLocations=3;
        config.numContainersPerLocation = 5;
        config.numLockedItemsPerLocation = 2;
        config.numMovableItems = 10;
    
        runFollowTheSunSoakTest(config);
    }
    
    @Test
    public void testLoadBalancingManyItemsQuickTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 1;
        config.numLocations=3;
        config.numContainersPerLocation = 5;
        config.numLockedItemsPerLocation = 2;
        config.numMovableItems = 100;
        config.numContainerStopsPerCycle = 1;
        config.numItemStopsPerCycle = 1;
    
        runFollowTheSunSoakTest(config);
    }
    
    @Test(groups={"Integration","Acceptance"}) // integration group, because it's slow to run many cycles
    public void testLoadBalancingSoakTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 100;
        config.numLocations=3;
        config.numContainersPerLocation = 5;
        config.numLockedItemsPerLocation = 2;
        config.numMovableItems = 10;
    
        runFollowTheSunSoakTest(config);
    }

    @Test(groups={"Integration","Acceptance"}) // integration group, because it's slow to run many cycles
    public void testLoadBalancingManyItemsSoakTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 100;
        config.numLocations=3;
        config.numContainersPerLocation = 5;
        config.numLockedItemsPerLocation = 2;
        config.numMovableItems = 100;
        config.numContainerStopsPerCycle = 3;
        config.numItemStopsPerCycle = 10;
        
        runFollowTheSunSoakTest(config);
    }

    @Test(groups={"Integration","Acceptance"}) // integration group, because it's slow to run many cycles
    public void testLoadBalancingManyManyItemsTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 1;
        config.numLocations=10;
        config.numContainersPerLocation = 5;
        config.numLockedItemsPerLocation = 100;
        config.numMovableItems = 1000;
        config.numContainerStopsPerCycle = 0;
        config.numItemStopsPerCycle = 0;
        config.timeout_ms = 30*1000;
        config.verbose = false;
        
        runFollowTheSunSoakTest(config);
    }
    
    private void runFollowTheSunSoakTest(RunConfig config) {
        int numCycles = config.numCycles;
        int numLocations = config.numLocations;
        int numContainersPerLocation = config.numContainersPerLocation;
        int numLockedItemsPerLocation = config.numLockedItemsPerLocation;
        int numMovableItems = config.numMovableItems;
        
        int numContainerStopsPerCycle = config.numContainerStopsPerCycle;
        int numItemStopsPerCycle = config.numItemStopsPerCycle;
        long timeout_ms = config.timeout_ms;
        final boolean verbose = config.verbose;
        
        MockItemEntityImpl.totalMoveCount.set(0);
        
        List<Location> locations = new ArrayList<Location>();
        Multimap<Location,MockContainerEntity> containers = HashMultimap.<Location,MockContainerEntity>create();
        Multimap<Location,MockItemEntity> lockedItems = HashMultimap.<Location,MockItemEntity>create();
        final List<MockItemEntity> movableItems = new ArrayList<MockItemEntity>();
        
        for (int i = 1; i <= numLocations; i++) {
            String locName = "loc"+i;
            Location loc = new SimulatedLocation(MutableMap.of("name",locName));
            locations.add(loc);
            
            for (int j = 1; j <= numContainersPerLocation; j++) {
                MockContainerEntity container = newContainer(app, loc, "container-"+locName+"-"+j);
                containers.put(loc, container);
            }
            for (int j = 1; j <= numLockedItemsPerLocation; j++) {
                MockContainerEntity container = Iterables.get(containers.get(loc), j%numContainersPerLocation);
                MockItemEntity item = newLockedItem(app, container, "item-locked-"+locName+"-"+j);
                lockedItems.put(loc, item);
            }
        }
        
        for (int i = 1; i <= numMovableItems; i++) {
            MockContainerEntity container = Iterables.get(containers.values(), i%containers.size());
            MockItemEntity item = newItem(app, container, "item-movable"+i);
            movableItems.add(item);
        }

        for (int i = 1; i <= numCycles; i++) {
            LOG.info("{}: cycle {}", FollowTheSunPolicySoakTest.class.getSimpleName(), i);
            
            // Stop movable items, and start others
            for (int j = 1; j <= numItemStopsPerCycle; j++) {
                int itemIndex = random.nextInt(numMovableItems);
                MockItemEntity itemToStop = movableItems.get(itemIndex);
                itemToStop.stop();
                LOG.debug("Unmanaging item {}", itemToStop);
                Entities.unmanage(itemToStop);
                movableItems.set(itemIndex, newItem(app, Iterables.get(containers.values(), 0), "item-movable"+itemIndex));
            }

            // Choose a location to be busiest
            int locIndex = random.nextInt(numLocations);
            final Location busiestLocation = locations.get(locIndex);
            
            // Repartition the load across the items
            for (int j = 0; j < numMovableItems; j++) {
                MockItemEntity item = movableItems.get(j);
                Map<Entity, Double> workrates = Maps.newLinkedHashMap();
                
                for (Map.Entry<Location,MockItemEntity> entry : lockedItems.entries()) {
                    Location location = entry.getKey();
                    MockItemEntity source = entry.getValue();
                    double baseWorkrate = (location == busiestLocation ? 1000 : 100);
                    double jitter = 10;
                    double jitteredWorkrate = Math.max(0, baseWorkrate + (random.nextDouble()*jitter*2 - jitter));
                    workrates.put(source, jitteredWorkrate);
                }
                ((EntityLocal)item).setAttribute(TEST_METRIC, workrates);
            }

            // Stop containers, and start others
            // This offloads the "immovable" items to other containers in the same location!
            for (int j = 1; j <= numContainerStopsPerCycle; j++) {
                int containerIndex = random.nextInt(containers.size());
                MockContainerEntity containerToStop = Iterables.get(containers.values(), containerIndex);
                Location location = Iterables.get(containerToStop.getLocations(), 0);
                MockContainerEntity otherContainerInLocation = Iterables.find(containers.get(location), Predicates.not(Predicates.equalTo(containerToStop)), null);
                containerToStop.offloadAndStop(otherContainerInLocation);
                LOG.debug("Unmanaging container {}", containerToStop);
                Entities.unmanage(containerToStop);
                containers.remove(location, containerToStop);
                
                MockContainerEntity containerToAdd = newContainer(app, location, "container-"+location.getDisplayName()+"-new."+i+"."+j);
                containers.put(location, containerToAdd);
            }

            // Assert that the items all end up in the location with maximum load-generation
            Asserts.succeedsEventually(MutableMap.of("timeout", timeout_ms), new Runnable() {
                public void run() {
                    Iterable<Location> itemLocs = Iterables.transform(movableItems, new Function<MockItemEntity, Location>() {
                        public Location apply(MockItemEntity input) {
                            BalanceableContainer<?> container = input.getAttribute(Movable.CONTAINER);
                            Collection<Location> locs = (container != null) ? container.getLocations(): null;
                            return (locs != null && locs.size() > 0) ? Iterables.get(locs, 0) : null;
                        }});
                    
                    Iterable<String> itemLocNames = Iterables.transform(itemLocs, new Function<Location, String>() {
                        public String apply(Location input) {
                            return (input != null) ? input.getDisplayName() : null;
                        }});
                    String errMsg;
                    if (verbose) {
                        errMsg = verboseDumpToString()+"; itemLocs="+itemLocNames;
                    } else {
                        Set<String> locNamesInUse = Sets.newLinkedHashSet(itemLocNames);
                        errMsg = "locsInUse="+locNamesInUse+"; totalMoves="+MockItemEntityImpl.totalMoveCount;
                    }
                    
                    assertEquals(ImmutableList.copyOf(itemLocs), Collections.nCopies(movableItems.size(), busiestLocation), errMsg);
                }});
        }
    }
    
    static class RunConfig {
        int numCycles = 1;
        int numLocations = 3;
        int numContainersPerLocation = 5;
        int numLockedItemsPerLocation = 5;
        int numMovableItems = 5;
        int numContainerStopsPerCycle = 0;
        int numItemStopsPerCycle = 0;
        long timeout_ms = TIMEOUT_MS;
        boolean verbose = true;
    }
}
