package org.apache.brooklyn.location.jclouds;

import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.effector.ParameterType;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.effector.EffectorAndBody;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.EffectorTasks.EffectorBodyTaskFactory;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.location.jclouds.networking.JcloudsPortForwarderExtension;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.Protocol;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.jclouds.compute.domain.NodeMetadata;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

public class JcloudsLocationReleasePortForwardingTest extends BrooklynAppLiveTestSupport {
    
    private Stopwatch stopwatch;
    private PortForwardManager portForwardManager;
    private JcloudsLocation loc;
    private NodeMetadata node;
    private JcloudsSshMachineLocation pseudoMachine;
    private RecordingJcloudsPortForwarderExtension portForwarder;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        stopwatch = Stopwatch.createStarted();
        portForwardManager = (PortForwardManager) mgmt.getLocationRegistry().resolve("portForwardManager(scope=global)");
        loc = (JcloudsLocation) mgmt.getLocationRegistry().resolve("jclouds:aws-ec2:us-east-1");

        node = Mockito.mock(NodeMetadata.class);
        Mockito.when(node.getId()).thenReturn("mynodeid");

        portForwarder = new RecordingJcloudsPortForwarderExtension(stopwatch);
        pseudoMachine = mgmt.getLocationManager().createLocation(LocationSpec.create(JcloudsSshMachineLocation.class)
                .configure("jcloudsParent", loc)
                .configure("address", "1.1.1.1")
                .configure("port", 2000)
                .configure("user", "myname")
                .configure("node", node)
                .configure(JcloudsLocation.USE_PORT_FORWARDING, true)
                .configure(JcloudsLocation.PORT_FORWARDER, portForwarder)
                .configure(JcloudsLocation.PORT_FORWARDING_MANAGER, portForwardManager));
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testReleasesSshPort() throws Exception {
        execRelease(loc, pseudoMachine);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                portForwarder.assertClosedEquals(ImmutableSet.of(HostAndPort.fromParts("1.1.1.1", 2000)));
            }});
    }
    
    @Test(groups={"Live", "Live-sanity"})
    public void testReleasesRecordedMappedPortsConcurrently() throws Exception {
        final List<HostAndPort> publicEndpoints = Lists.newArrayList();
        publicEndpoints.add(HostAndPort.fromString("1.1.1.1:2000"));
        
        for (int i = 0; i < 60; i++) {
            HostAndPort publicEndpoint = HostAndPort.fromString("2.2.2.2:"+(2000+i));
            portForwardManager.associate("myid", publicEndpoint, pseudoMachine, 1+i);
            publicEndpoints.add(publicEndpoint);
        }
        portForwarder.setSleepBeforeReturning(Duration.ONE_SECOND);

        Duration preReleaseTimestamp = Duration.of(stopwatch);
        execRelease(loc, pseudoMachine);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                portForwarder.assertClosedEquals(publicEndpoints);
            }});

        Duration releaseTime = Duration.of(stopwatch).subtract(preReleaseTimestamp);

        // If done sequentially, it would have taken 60 seconds. We'll allow 30 seconds
        // because we've seen jenkins be extremely slow when running unit tests on apache
        // shared infrastructure.
        assertTrue(releaseTime.isShorterThan(Duration.THIRTY_SECONDS), "releaseTime="+releaseTime);
        assertTrue(releaseTime.toMilliseconds() - Duration.ONE_SECOND.toMilliseconds() >= 0, "releaseTime="+releaseTime);
    }
    
    /**
     * Records calls to openPortForwarding and closePortForwarding. Optionally does a sleep during each call.
     */
    static class RecordingJcloudsPortForwarderExtension implements JcloudsPortForwarderExtension {
        private final List<List<Object>> calls = Lists.newCopyOnWriteArrayList();
        private final AtomicInteger nextPort = new AtomicInteger(11000);
        private final Stopwatch stopwatch;
        private Duration sleepBeforeReturning;
        
        public RecordingJcloudsPortForwarderExtension(Stopwatch stopwatch) {
            this.stopwatch = stopwatch;
            this.sleepBeforeReturning = Duration.ZERO;
        }
        public void setSleepBeforeReturning(Duration val) {
            this.sleepBeforeReturning = val;
        }
        @Override
        public HostAndPort openPortForwarding(NodeMetadata node, int targetPort, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
            calls.add(ImmutableList.of("open", Duration.of(stopwatch), node, targetPort, optionalPublicPort, protocol, accessingCidr));
            Time.sleep(sleepBeforeReturning);
            if (optionalPublicPort.isPresent()) {
                return HostAndPort.fromParts("2.2.2.2", optionalPublicPort.get());
            } else {
                return HostAndPort.fromParts("2.2.2.2", nextPort.get());
            }
        }
        @Override
        public void closePortForwarding(NodeMetadata node, int targetPort, HostAndPort publicHostAndPort, Protocol protocol) {
            calls.add(ImmutableList.of("close", System.currentTimeMillis(), node, targetPort, publicHostAndPort, protocol));
            Time.sleep(sleepBeforeReturning);
        }
        public void assertClosedEquals(Iterable<? extends HostAndPort> expected) {
            List<HostAndPort> closed = Lists.newArrayList();
            for (List<Object> call : calls) {
                if ("close".equals(call.get(0))) closed.add((HostAndPort) call.get(4));
            }
            Asserts.assertEqualsIgnoringOrder(closed, expected);
        }
    }
    
    // Task execution unfortunately assumes that it is executing inside an "execution context". 
    // It fails (only logging at debug!) if it is not. Therefore, we execute the releasePortForwarding
    // inside an effector.
    private void execRelease(final JcloudsLocation loc, final JcloudsSshMachineLocation machine) throws Exception {
        EffectorBody<Void> effectorBody = new EffectorBody<Void>() {
            public Void call(ConfigBag parameters) {
                loc.releasePortForwarding(machine);
                return null;
            }
        };
        Effector<Void> effector = new EffectorAndBody<Void>("myeffector", Void.class, ImmutableList.<ParameterType<?>>of(), "", 
                new EffectorBodyTaskFactory<Void>(effectorBody));
        EntityInternal entity = (EntityInternal) app.createAndManageChild(EntitySpec.create(BasicEntity.class));
        entity.getMutableEntityType().addEffector(effector);
        entity.invoke(effector, ImmutableMap.<String, Object>of()).get();
    }
}
