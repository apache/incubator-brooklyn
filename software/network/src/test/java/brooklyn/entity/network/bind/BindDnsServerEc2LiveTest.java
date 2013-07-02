package brooklyn.entity.network.bind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class BindDnsServerEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        BindDnsServer dns = app.createAndManageChild(EntitySpecs.spec(BindDnsServer.class));

        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(dns, BindDnsServer.SERVICE_UP, true);
    }

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
