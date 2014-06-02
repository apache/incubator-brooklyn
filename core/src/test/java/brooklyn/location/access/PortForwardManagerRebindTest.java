package brooklyn.location.access;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Networking;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

public class PortForwardManagerRebindTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManagerRebindTest.class);

    private Map<HostAndPort, HostAndPort> portMapping;
    private String machineAddress = "1.2.3.4";
    private SshMachineLocation origSimulatedMachine;

    
    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        origSimulatedMachine = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getInetAddressWithFixedName(machineAddress))
                .configure("port", 1234)
                .configure("user", "myuser"));
    }
    
    @Test
    public void testHostAndPortTransformingEnricher() throws Exception {
        String publicIpId = "5.6.7.8";
        String publicAddress = "5.6.7.8";

        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntity.class));
        PortForwardManager origPortForwardManager = origEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);

        // We first wait for persisted, to ensure that it is the PortForwardManager.onChanged that is causing persistence.
        RebindTestUtils.waitForPersisted(origApp);
        origPortForwardManager.recordPublicIpHostname(publicIpId, publicAddress);
        origPortForwardManager.acquirePublicPortExplicit(publicIpId, 40080);
        origPortForwardManager.associate(publicIpId, 40080, origSimulatedMachine, 80);
     
        newApp = rebind(false);
        
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        Location newSimulatedMachine = newApp.getManagementContext().getLocationManager().getLocation(origSimulatedMachine.getId());
        PortForwardManager newPortForwardManager = newEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);
        
        assertEquals(newPortForwardManager.getPublicIpHostname(publicIpId), publicAddress);
        assertEquals(newPortForwardManager.lookup(newSimulatedMachine, 80), HostAndPort.fromParts(publicAddress, 40080));
    }
    
    public interface HasPfm {
        PortForwardManager getPfm();
    }
    
    public static class MyEntity extends TestEntityImpl implements HasPfm {
        public static final ConfigKey<PortForwardManager> PORT_FORWARD_MANAGER = ConfigKeys.newConfigKey(PortForwardManager.class, "myentity.portForwardManager");
        public static final AttributeSensor<PortForwardManager> PORT_FORWARD_MANAGER_LIVE = Sensors.newSensor(PortForwardManager.class, "myentity.portForwardManager.live");

        @Override
        public void init() {
            super.init();
            
            if (getConfig(PORT_FORWARD_MANAGER) == null) {
                PortForwardManagerAuthority pfm = new PortForwardManagerAuthority();
                pfm.injectOwningEntity(this);
                setAttribute(PORT_FORWARD_MANAGER_LIVE, pfm);
                setConfig(PORT_FORWARD_MANAGER, PortForwardManagerClient.fromMethodOnEntity(this, "getPfm"));
            }
        }
        
        @Override
        public PortForwardManager getPfm() {
            return getAttribute(PORT_FORWARD_MANAGER_LIVE);
        }
        
        @SetFromFlag("myconfigflagname")
        public static final ConfigKey<String> MY_CONFIG_WITH_FLAGNAME = ConfigKeys.newStringConfigKey("myentity.myconfigwithflagname");
    }
    
}
