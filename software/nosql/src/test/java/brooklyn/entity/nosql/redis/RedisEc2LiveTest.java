package brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import redis.clients.jedis.Connection;
import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RedisEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RedisEc2LiveTest.class);

    @Test(groups = {"Live"})
    public void test_CentOS_6_3() throws Exception {
        // Image: {id=us-east-1/ami-7d7bfc14, providerId=ami-7d7bfc14, name=RightImage_CentOS_6.3_x64_v5.8.8.5, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.0, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, version=5.8.8.5, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-7d7bfc14", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Override
    protected void doTest(Location loc) throws Exception {
        // Start Redis
        RedisStore redis = app.createAndManageChild(EntitySpecs.spec(RedisStore.class));
        app.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(redis, RedisStore.SERVICE_UP, true);

        // Access Redis
        Connection connection = getRedisConnection(redis);
        assertTrue(connection.isConnected());
        connection.disconnect();
    }

    private Connection getRedisConnection(RedisStore redis) {
        String hostname = redis.getAttribute(RedisStore.HOSTNAME);
        int port = redis.getAttribute(RedisStore.REDIS_PORT);
        Connection connection = new Connection(hostname, port);
        connection.connect();
        return connection;
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
