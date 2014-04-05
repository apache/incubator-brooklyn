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

//    @AfterMethod(alwaysRun=false)
//    @Override
//    public void tearDown() throws Exception {
//        //don't tearDown
//    }
}
