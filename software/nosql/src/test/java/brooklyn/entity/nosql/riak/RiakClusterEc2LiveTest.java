package brooklyn.entity.nosql.riak;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Created by zaid.mohsin on 06/04/2014.
 */
public class RiakClusterEc2LiveTest extends AbstractEc2LiveTest {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        RiakCluster entity = app.createAndManageChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, 3)
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)));
                app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(entity, RiakNode.SERVICE_UP, true);

    }



    @Test(enabled = false)
    public void testDummy() {
    } // Convince TestNG IDE integration that this really does have test methods

    @AfterMethod(alwaysRun=false)
    @Override
    public void tearDown() throws Exception {
        //don't tearDown
    }
}
