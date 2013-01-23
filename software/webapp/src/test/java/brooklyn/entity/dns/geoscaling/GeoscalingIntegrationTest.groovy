package brooklyn.entity.dns.geoscaling

import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.geo.HostGeoInfo
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Predicates

/**
 * {@link GeoscalingScriptGenerator} unit tests.
 */
class GeoscalingIntegrationTest {
    static { TimeExtras.init() }

    private final static Set<HostGeoInfo> HOSTS = [
        new HostGeoInfo("1.2.3.100", "Server 1", 40.0, -80.0),
        new HostGeoInfo("1.2.3.101", "Server 2", 30.0, 20.0)
    ]

    private final String primaryDomain = "geopaas.org"//"domain"+((int)(Math.random()*10000))+".test.org";
    private final String subDomain = "subdomain"+((int)(Math.random()*10000));
    private final InetAddress addr = InetAddress.localHost
    private final SshMachineLocation loc = new SshMachineLocation(address:addr, name:'Edinburgh', latitude : 55.94944, longitude : -3.16028, iso3166 : ["GB-EDH"])
    
    @Test(groups=["Integration"])
    public void testRoutesToExpectedLocation() {
        TestApplication app = ApplicationBuilder.builder(TestApplication.class).manage();
        TestEntity target = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        target.setAttribute(Attributes.HOSTNAME,addr.getHostName())
        
        DynamicGroup group = app.createAndManageChild(BasicEntitySpec.newInstance(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class)));
        
        GeoscalingDnsService geoDns = app.createAndManageChild(BasicEntitySpec.newInstance(GeoscalingDnsService.class)
                .displayName("Geo-DNS")
                .configure("username", "cloudsoft")
                .configure("password", "cl0uds0ft")
                .configure("primaryDomainName", primaryDomain)
                .configure("smartSubdomainName", subDomain));
        geoDns.setTargetEntityProvider(group)
        
        app.start([loc])
        
        println("geo-scaling test, using $subDomain.$primaryDomain; expect to be wired to $addr")
        
        new Repeater("Wait for target hosts")
            .repeat()
            .every(500 * MILLISECONDS)
            .until { geoDns.getTargetHosts().size() == 1 }
            .limitIterationsTo(20)
            .run();
        
        assertEquals(geoDns.getTargetHosts().size(), 1);
    }
}
