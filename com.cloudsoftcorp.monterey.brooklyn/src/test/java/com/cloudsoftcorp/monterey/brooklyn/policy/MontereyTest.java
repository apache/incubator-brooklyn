package com.cloudsoftcorp.monterey.brooklyn.policy;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.After;
import org.testng.Before;
import org.testng.Test;

import brooklyn.policy.BalancerPolicy;
import brooklyn.policy.ResizerPolicy;

import com.cloudsoftcorp.monterey.CloudsoftThreadMonitoringTestFixture;
import com.cloudsoftcorp.monterey.brooklyn.policy.MediatorEntity;
import com.cloudsoftcorp.monterey.brooklyn.policy.ResizableMediatorTier;
import com.cloudsoftcorp.monterey.brooklyn.policy.SegmentEntity;
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation;
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo;
import com.cloudsoftcorp.monterey.network.control.plane.ManagementNode;
import com.cloudsoftcorp.monterey.network.mocks.SimpleCapturingLppClientStub;
import com.cloudsoftcorp.monterey.network.mocks.SimpleCapturingSegmentProcessor;
import com.cloudsoftcorp.monterey.network.tests.echo.DmnAssertionUtils;
import com.cloudsoftcorp.monterey.network.tests.echo.DmnAssertionUtils.BackgroundPublisher;
import com.cloudsoftcorp.monterey.network.tests.echo.DmnNetworkFixture;
import com.cloudsoftcorp.monterey.network.tests.echo.DmnRolloutUtils;
import com.cloudsoftcorp.monterey.node.api.NodeId;
import com.cloudsoftcorp.util.Loggers;
import com.cloudsoftcorp.util.collections.CollectionsUtils;
import com.cloudsoftcorp.util.condition.Conditions.AbstractCondition;
import com.google.common.collect.ImmutableSet;

public class MontereyTest extends CloudsoftThreadMonitoringTestFixture {

    @SuppressWarnings("unused")
    private static final Logger LOG = Loggers.getLoggerForClass();
    static {
        Logger.getLogger("com.cloudsoftcorp.entitier.policy").setLevel(Level.FINEST);
    }
    
    private static final int TIMEOUT = 10*1000;
    
    private DmnNetworkFixture stuff;
    private DmnRolloutUtils roller;
    private DmnAssertionUtils asserter;
    private Dmn1NetworkInfo networkInfo;

    @Before
    public void setUp() throws Exception {
        SimpleCapturingLppClientStub.reset();
        SimpleCapturingSegmentProcessor.reset();
        stuff = new DmnNetworkFixture.Builder().useWebApi().build();
        roller = stuff.getRoller();
        asserter = stuff.getAsserter();
        networkInfo = stuff.getNetworkInfo();
    }
    
    @After
    public void tearDown() throws Exception {
        if (stuff != null) stuff.tearDown();
    }

    @Test
    public void testResizesMontereyNetworkUsingLowAndHighWatermarks() throws Exception {
        ManagementNode managementNode = stuff.getManagementNode();
        MontereyActiveLocation location = CollectionsUtils.getAt(networkInfo.getActiveLocations(), 0);
        List<String> segments = roller.allocateSegments(10);
        roller.initNodes(location, 1, 1, 1, 1, 0);
        roller.createAndWaitForNodes(location, 3);
        Map<String,BackgroundPublisher> publishers = new LinkedHashMap<String,BackgroundPublisher>();
        Map<MontereyActiveLocation,ResizableMediatorTier> tiersByLocation = new LinkedHashMap<MontereyActiveLocation,ResizableMediatorTier>();
        Map<MontereyActiveLocation,ResizerPolicy<?>> policiesByLocation = new LinkedHashMap<MontereyActiveLocation,ResizerPolicy<?>>();
        
        for (String segment : segments) {
            publishers.put(segment, asserter.newLppPublisher(roller.pickAnyLpp(), segment, 0));
        }
        
        for (MontereyActiveLocation loc : stuff.getNetworkInfo().getActiveLocations()) {
            ResizableMediatorTier entity = new ResizableMediatorTier(loc, managementNode);
            ResizerPolicy<Double> resizerPolicy = new ResizerPolicy<Double>();
            resizerPolicy.setEntity(entity);
            resizerPolicy.setMetricName(new String[] {ResizableMediatorTier.AVERAGE_MSGS_PER_SEC_INBOUND_METRIC});
            resizerPolicy.setMetricLowerBound(7d);
            resizerPolicy.setMetricUpperBound(15d);
            resizerPolicy.setMinSize(1);
            resizerPolicy.postConstruct();
            
            tiersByLocation.put(loc, entity);
            policiesByLocation.put(loc, resizerPolicy);
        }
        
        // Set total workrate to 2*10, so average of 20; expect need two mediator
        // that will take average to 2*10/2 = 10
        for (BackgroundPublisher publisher : publishers.values()) {
            publisher.setWorkrate(2);
        }
        asserter.assertAllValid(1, 1, 2, 1, 0, 2, segments);
        
        // Increase total workrate to 4*10, so average 20; expect need three mediator
        // that will take average to 4*10/3 = 13.3
        for (BackgroundPublisher publisher : publishers.values()) {
            publisher.setWorkrate(4);
        }
        asserter.assertAllValid(1, 1, 3, 1, 0, 1, segments);
        
        // Now decrease workrate to 1*10, so avarage 3.3; expect mediators to be reverted
        // that will take average to 1*10/1 = 10
        for (BackgroundPublisher publisher : publishers.values()) {
            publisher.setWorkrate(1);
        }
        asserter.assertAllValid(1, 1, 1, 1, 0, 3, segments);
    }
    
    @Test
    public void testBalancesMontereyNetworkUsingSegmentWorkrates() throws Exception {
        ManagementNode managementNode = stuff.getManagementNode();
        MontereyActiveLocation location = CollectionsUtils.getAt(networkInfo.getActiveLocations(), 0);
        List<String> segments = roller.allocateSegments(6);
        roller.initNodes(location, 1, 1, 1, 1, 0);
        roller.initNodes(location, 0, 0, 2, 0, 0);
        Map<String,BackgroundPublisher> publishers = new LinkedHashMap<String,BackgroundPublisher>();
        Map<MontereyActiveLocation,ResizableMediatorTier> tiersByLocation = new LinkedHashMap<MontereyActiveLocation,ResizableMediatorTier>();
        Map<MontereyActiveLocation,BalancerPolicy<?>> policiesByLocation = new LinkedHashMap<MontereyActiveLocation,BalancerPolicy<?>>();
        
        for (String segment : segments) {
            publishers.put(segment, asserter.newLppPublisher(roller.pickAnyLpp(), segment, 0));
        }
        
        for (MontereyActiveLocation loc : stuff.getNetworkInfo().getActiveLocations()) {
            ResizableMediatorTier entity = new ResizableMediatorTier(loc, managementNode);
            BalancerPolicy<Double> balancerPolicy = new BalancerPolicy<Double>();
            balancerPolicy.setEntity(entity);
            balancerPolicy.setBalanceableSubEntityWorkrateMetricName(new String[] {MediatorEntity.MSGS_PER_SEC_INBOUND_METRIC});
            balancerPolicy.setMoveableEntityWorkrateMetricName(new String[] {SegmentEntity.MSGS_PER_SEC_INBOUND_METRIC});
            balancerPolicy.postConstruct();
            
            tiersByLocation.put(loc, entity);
            policiesByLocation.put(loc, balancerPolicy);
        }
        
        // Expect it to balance so equal number of segments on each mediator
        for (BackgroundPublisher publisher : publishers.values()) {
            publisher.setWorkrate(6);
        }
        asserter.assertAllValid(1, 1, 3, 1, 0, 0, segments);
        asserter.assertHasDistributedSegments(segments);
        waitUtils.assertTrueWithin(TIMEOUT, new AbstractCondition("balanced segments") {
            @Override public Boolean evaluate() {
                for (NodeId mediator : roller.getAllMs()) {
                    if (2 != networkInfo.getSegmentsAtNode(mediator).size()) {
                        return false;
                    }
                }
                return true;
            }
            @Override public String getMessage() {
                return networkInfo.getSegmentAllocations().toString();
            }});
        
        // average on each should be 24
        publishers.get(segments.get(0)).setWorkrate(20);
        publishers.get(segments.get(1)).setWorkrate(4);
        publishers.get(segments.get(2)).setWorkrate(16);
        publishers.get(segments.get(3)).setWorkrate(8);
        publishers.get(segments.get(4)).setWorkrate(12);
        publishers.get(segments.get(5)).setWorkrate(12);
        
        final Set<Set<String>> expectedSegmentAllocation = ImmutableSet.of(
                (Set<String>)ImmutableSet.of(segments.get(0), segments.get(1)),
                (Set<String>)ImmutableSet.of(segments.get(2), segments.get(3)),
                (Set<String>)ImmutableSet.of(segments.get(4), segments.get(5)));
        
        waitUtils.assertTrueWithin(TIMEOUT, new AbstractCondition("balanced segments") {
            @Override public Boolean evaluate() {
                Set<Set<String>> actualSegmentAllocation = new HashSet<Set<String>>();
                for (NodeId mediator : roller.getAllMs()) {
                    actualSegmentAllocation.add(new HashSet<String>(networkInfo.getSegmentsAtNode(mediator)));
                }
                return expectedSegmentAllocation.equals(actualSegmentAllocation);
            }
            @Override public String getMessage() {
                return networkInfo.getSegmentAllocations().toString();
            }});
    }
}
