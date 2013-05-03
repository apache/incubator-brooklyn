package brooklyn.entity.dns.geoscaling;

import static org.testng.Assert.assertEquals;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.NetworkUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.Repeater;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * {@link GeoscalingScriptGenerator} unit tests.
 */
public class GeoscalingIntegrationTest {

    protected static final Logger LOG = LoggerFactory.getLogger(GeoscalingIntegrationTest.class);

    private final static Set<HostGeoInfo> HOSTS = ImmutableSet.of(
        new HostGeoInfo("1.2.3.100", "Server 1", 40.0, -80.0),
        new HostGeoInfo("1.2.3.101", "Server 2", 30.0, 20.0)
    );

    private final String primaryDomain = "geopaas.org";//"domain"+((int)(Math.random()*10000))+".test.org";
    private final String subDomain = "subdomain"+((int)(Math.random()*10000));
    private final InetAddress addr = NetworkUtils.getLocalHost();
    private final SshMachineLocation loc = new SshMachineLocation(MutableMap.builder()
            .put("address", addr)
            .put("name", "Edinburgh")
            .put("latitude", 55.94944)
            .put("longitude", -3.16028)
            .put("iso3166", ImmutableList.of("GB-EDH"))
            .build());
    
    @Test(groups={"Integration"})
    public void testRoutesToExpectedLocation() {
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class);
        TestEntity target = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        target.setAttribute(Attributes.HOSTNAME,addr.getHostName());
        
        DynamicGroup group = app.createAndManageChild(EntitySpecs.spec(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class)));
        
        final GeoscalingDnsService geoDns = app.createAndManageChild(EntitySpecs.spec(GeoscalingDnsService.class)
                .displayName("Geo-DNS")
                .configure("username", "cloudsoft")
                .configure("password", "cl0uds0ft")
                .configure("primaryDomainName", primaryDomain)
                .configure("smartSubdomainName", subDomain)
                .configure("targetEntityProvider", group));
        
        app.start(ImmutableList.of(loc));
        
        LOG.info("geo-scaling test, using {}.{}; expect to be wired to {}", new Object[] {subDomain, primaryDomain, addr});
        
        new Repeater("Wait for target hosts")
            .repeat()
            .every(500, TimeUnit.MILLISECONDS)
            .until(new Callable<Boolean>() {
                public Boolean call() {
                    return geoDns.getTargetHosts().size() == 1;
                }})
            .limitIterationsTo(20)
            .run();
        
        assertEquals(geoDns.getTargetHosts().size(), 1);
    }
}
