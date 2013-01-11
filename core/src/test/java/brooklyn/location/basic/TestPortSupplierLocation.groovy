package brooklyn.location.basic;

import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.test.entity.TestApplication2
import brooklyn.test.entity.TestEntity

public class TestPortSupplierLocation {

    SimulatedLocation l;
    PortAttributeSensorAndConfigKey ps;
    TestApplication2 app;
    TestEntity e;
    
    @BeforeMethod
    public void setup() {
        l = new SimulatedLocation();
        app = ApplicationBuilder.builder(TestApplication2.class).manage();
        e = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        app.start([l]);
        
        ps = new PortAttributeSensorAndConfigKey("some.port", "for testing", "1234+");
    }
    
    @Test
    public void testObtainsPort() {
        e.setAttribute(ps);
        
        int p = e.getAttribute(ps);
        Assert.assertEquals(p, 1234);
        
        //sensor access should keep the same value
        p = e.getAttribute(ps);
        Assert.assertEquals(p, 1234);
    }
    
    @Test
    public void testRepeatedConvertAccessIncrements() {
        int p = ps.getAsSensorValue(e);
        Assert.assertEquals(p, 1234);

        //but direct access should see it as being reserved (not required behaviour, but it is the current behaviour)
        int p2 = ps.getAsSensorValue(e);
        Assert.assertEquals(p2, 1235);
    }

    @Test
    public void testNullBeforeSetting() {
        // currently getting the attribute before explicitly setting return null; i.e. no "auto-set" -- 
        // but this behaviour may be changed
        Integer p = e.getAttribute(ps);
        Assert.assertEquals(p, null);
    }

    @Test
    public void testSimulatedRestrictedPermitted() {
        l.setPermittedPorts(PortRanges.fromString("1240+"));
        
        e.setAttribute(ps);
        int p = e.getAttribute(ps);
        Assert.assertEquals((int)p, 1240);
    }

}
