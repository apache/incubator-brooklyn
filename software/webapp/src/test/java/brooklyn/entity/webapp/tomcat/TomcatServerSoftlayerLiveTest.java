package brooklyn.entity.webapp.tomcat;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertNotNull;

import java.net.URL;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractSoftlayerLiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * A simple test of installing+running on Softlayer, using various OS distros and versions. 
 */
public class TomcatServerSoftlayerLiveTest extends AbstractSoftlayerLiveTest {
    
    private URL warUrl = checkNotNull(getClass().getClassLoader().getResource("hello-world.war"));
    
    @Override
    protected void doTest(Location loc) throws Exception {
        final TomcatServer server = app.createAndManageChild(EntitySpec.create(TomcatServer.class)
                .configure("war", warUrl.toString()));
        
        app.start(ImmutableList.of(loc));
        
        String url = server.getAttribute(TomcatServer.ROOT_URL);
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentContainsText(url, "Hello");
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(server.getAttribute(TomcatServer.REQUEST_COUNT));
                assertNotNull(server.getAttribute(TomcatServer.ERROR_COUNT));
                assertNotNull(server.getAttribute(TomcatServer.TOTAL_PROCESSING_TIME));
                
                // TODO These appear not to be set in TomcatServerImpl.connectSensors
                //      See TomcatServerEc2LiveTest, where these are also not included.
//                assertNotNull(server.getAttribute(TomcatServer.MAX_PROCESSING_TIME));
//                assertNotNull(server.getAttribute(TomcatServer.BYTES_RECEIVED));
//                assertNotNull(server.getAttribute(TomcatServer.BYTES_SENT));
            }});
    }

    @Test(groups = {"Live", "Live-sanity"})
    @Override
    public void test_Ubuntu_12_0_4() throws Exception {
        super.test_Ubuntu_12_0_4();
    }
}
