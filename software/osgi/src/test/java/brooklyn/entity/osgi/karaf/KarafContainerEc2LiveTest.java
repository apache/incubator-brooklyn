package brooklyn.entity.osgi.karaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.internal.LanguageUtils;

import com.google.common.collect.ImmutableList;

public class KarafContainerEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(KarafContainerEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        KarafContainer karaf = app.createAndManageChild(BasicEntitySpec.newInstance(KarafContainer.class)
                .configure("name", LanguageUtils.newUid())
                .configure("displayName", "Karaf Test")
                .configure("jmxPort", "8099+")
                .configure("rmiServerPort", "9099+"));
        
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(karaf, KarafContainer.SERVICE_UP, true);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
