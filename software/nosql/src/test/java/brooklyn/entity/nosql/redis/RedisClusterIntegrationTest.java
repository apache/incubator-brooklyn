package brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;

public class RedisClusterIntegrationTest {

    private TestApplication app;
    private Location loc;
    private RedisCluster cluster;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = { "Integration" })
    public void testRedisClusterReplicates() throws Exception {
        final String key = "mykey";
        final String val = "1234567890";
        
        cluster = app.createAndManageChild(EntitySpecs.spec(RedisCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, true);

        RedisStore master = cluster.getMaster();
        List<RedisSlave> slaves = ImmutableList.<RedisSlave>copyOf((Collection)cluster.getSlaves().getMembers());
        
        assertEquals(slaves.size(), 3);
        
        JedisSupport viaMaster = new JedisSupport(master);
        viaMaster.writeData(key, val);
        assertEquals(viaMaster.readData(key), val);

        for (RedisSlave slave : slaves) {
            final JedisSupport viaSlave = new JedisSupport(slave);
            Asserts.succeedsEventually(new Callable<Void>() {
                @Override public Void call() throws Exception {
                    assertEquals(viaSlave.readData(key), val);
                    return null;
                }});
        }

        // Check that stopping slave will not stop anything else
        // (it used to stop master because wasn't supplying port!)
        slaves.get(0).stop();
        EntityTestUtils.assertAttributeEqualsEventually(slaves.get(0), Startable.SERVICE_UP, false);
        
        assertEquals(master.getAttribute(Startable.SERVICE_UP), Boolean.TRUE);
        for (RedisSlave slave : slaves.subList(1, slaves.size())) {
            assertEquals(slave.getAttribute(Startable.SERVICE_UP), Boolean.TRUE);
        }
        
        // Check that stopping cluster will stop everything
        cluster.stop();

        EntityTestUtils.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, false);
        assertEquals(master.getAttribute(Startable.SERVICE_UP), Boolean.FALSE);
        for (RedisSlave slave : slaves) {
            assertEquals(slave.getAttribute(Startable.SERVICE_UP), Boolean.FALSE);
        }
    }
}
