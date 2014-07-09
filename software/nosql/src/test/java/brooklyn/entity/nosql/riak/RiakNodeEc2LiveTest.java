package brooklyn.entity.nosql.riak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

public class RiakNodeEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        RiakNode entity = app.createAndManageChild(EntitySpec.create(RiakNode.class));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(entity, RiakNode.SERVICE_UP, true);

    }

    @Test(enabled = false)
    public void testDummy() {
    } // Convince TestNG IDE integration that this really does have test methods


}
