package brooklyn.entity.dns.geoscaling

import static org.testng.AssertJUnit.*

import java.util.LinkedHashSet
import java.util.Set

import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.dns.HostGeoInfo
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestEntity


/**
 * {@link GeoscalingScriptGenerator} unit tests.
 */
class GeoscalingIntegrationTest {
    
    private final static Set<HostGeoInfo> HOSTS = new LinkedHashSet<HostGeoInfo>();
    static {
        HOSTS.add(new HostGeoInfo("1.2.3.100", "Server 1", 40.0, -80.0));
        HOSTS.add(new HostGeoInfo("1.2.3.101", "Server 2", 30.0, 20.0));
    }
    
    private final String primaryDomain = "geopaas.org"//"domain"+((int)(Math.random()*10000))+".test.org";
    private final String subDomain = "subdomain"+((int)(Math.random()*10000));

    @Test(enabled=false, groups = [ "WIP" ])
    public void testRoutesToExpectedLocation() {
        InetAddress addr = InetAddress.localHost
        SshMachineLocation loc = new SshMachineLocation(address:addr, name:'Edinburgh', latitude : 55.94944, longitude : -3.16028, iso3166 : ["GB-EDH"])

        AbstractApplication app = new AbstractApplication() {}
        TestEntity target = new TestEntity(owner:app)
        DynamicGroup group = new DynamicGroup([:], app, { Entity e -> (e instanceof TestEntity) })
        GeoscalingDnsService geoDns = new GeoscalingDnsService([displayName: 'Geo-DNS',
                username: 'cloudsoft', password: 'cl0uds0ft', primaryDomainName: primaryDomain, smartSubdomainName: subDomain],
                app)
        geoDns.setTargetEntityProvider(group)
        app.start([loc])
        
        println("geo-scaling test, using $subDomain.$primaryDomain; expect to be wired to $addr")
        
        Thread.sleep(10000000)
    }
}
