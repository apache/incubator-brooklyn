package brooklyn.entity.nosql.cassandra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.nosql.redis.RedisStore;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class CassandraNodeEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger log = LoggerFactory.getLogger(CassandraNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        log.info("Testing Cassandra on {}", loc);

        CassandraNode cassandra = app.createAndManageChild(EntitySpecs.spec(CassandraNode.class)
                .configure("thriftPort", "9876+")
                .configure("clusterName", "TestCluster"));
        app.start(ImmutableList.of(loc));
        
        EntityTestUtils.assertAttributeEqualsEventually(cassandra, RedisStore.SERVICE_UP, true);

        // FIXME Extract astyanaxTest so can call from something other than sub-class of CassandraNodeIntegrationTest
        //astyanaxTest();
    }
}
