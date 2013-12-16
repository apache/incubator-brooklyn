package brooklyn.entity.messaging.zookeeper;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.ZooKeeperNode;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class ZooKeeperEc2LiveTest extends AbstractEc2LiveTest {

    /**
     * Test that can install, start and use a Zookeeper instance.
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        ZooKeeperNode zookeeper = app.createAndManageChild(EntitySpec.create(ZooKeeperNode.class).configure("jmxPort", "31001+"));
        app.start(ImmutableList.of(loc));
        Entities.dumpInfo(zookeeper);
        EntityTestUtils.assertAttributeEqualsEventually(zookeeper, Startable.SERVICE_UP, true);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods
}
