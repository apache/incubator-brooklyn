package brooklyn.entity.messaging.storm;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.ZooKeeperNode;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class StormEc2LiveTest extends AbstractEc2LiveTest {

    /**
     * Test that can install, start and use a Storm cluster: 1 nimbus, 1 zookeeper, 1 supervisor (worker node).
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        ZooKeeperNode zookeeper = app.createAndManageChild(EntitySpec.create(ZooKeeperNode.class));
        Storm nimbus = app.createAndManageChild(EntitySpec.create(Storm.class).configure("storm.role",
                Storm.Role.NIMBUS));
        Storm supervisor = app.createAndManageChild(EntitySpec.create(Storm.class).configure("storm.role",
                Storm.Role.SUPERVISOR));
        Storm ui = app.createAndManageChild(EntitySpec.create(Storm.class).configure("storm.role",
                Storm.Role.UI));        
        app.start(ImmutableList.of(loc));
        Entities.dumpInfo(app);
        
        EntityTestUtils.assertAttributeEqualsEventually(zookeeper, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(nimbus, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(supervisor, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(ui, Startable.SERVICE_UP, true);
    }

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods
}
