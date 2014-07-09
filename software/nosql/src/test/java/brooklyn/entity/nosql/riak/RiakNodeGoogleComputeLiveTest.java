package brooklyn.entity.nosql.riak;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import brooklyn.entity.AbstractGoogleComputeLiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

public class RiakNodeGoogleComputeLiveTest extends AbstractGoogleComputeLiveTest {
    @Override
    protected void doTest(Location loc) throws Exception {
        RiakCluster cluster = app.createAndManageChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, 2)
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, RiakCluster.SERVICE_UP, true);

        RiakNode first = (RiakNode) Iterables.get(cluster.getMembers(), 0);
        RiakNode second = (RiakNode) Iterables.get(cluster.getMembers(), 1);

        EntityTestUtils.assertAttributeEqualsEventually(first, RiakNode.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, RiakNode.SERVICE_UP, true);

        EntityTestUtils.assertAttributeEqualsEventually(first, RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true);

    }

    @Test(groups = {"Live"})
    @Override
    public void test_DefaultImage() throws Exception {
        super.test_DefaultImage();
    }

    @Test(enabled = false)
    public void testDummy() {
    } // Convince testng IDE integration that this really does have test methods

}
