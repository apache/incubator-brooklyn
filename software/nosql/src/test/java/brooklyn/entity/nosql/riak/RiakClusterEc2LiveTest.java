package brooklyn.entity.nosql.riak;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class RiakClusterEc2LiveTest extends AbstractEc2LiveTest {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        RiakCluster cluster = app.createAndManageChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, 3)
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, RiakNode.SERVICE_UP, true);

        RiakNode first = (RiakNode) Iterables.get(cluster.getMembers(), 0);
        RiakNode second = (RiakNode) Iterables.get(cluster.getMembers(), 1);

        EntityTestUtils.assertAttributeEqualsEventually(first, RiakNode.RIAK_NODE_IN_CLUSTER, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, RiakNode.RIAK_NODE_IN_CLUSTER, true);
    }


    @Test(enabled = false)
    public void testDummy() {
    } // Convince TestNG IDE integration that this really does have test methods

}
