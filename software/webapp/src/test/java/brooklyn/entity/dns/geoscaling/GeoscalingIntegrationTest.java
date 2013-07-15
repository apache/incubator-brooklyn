package brooklyn.entity.dns.geoscaling;

import static org.testng.Assert.assertEquals;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.location.geo.HostGeoLookup;
import brooklyn.location.geo.MaxMindHostGeoLookup;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.NetworkUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.BrooklynSystemProperties;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

/**
 * {@link GeoscalingScriptGenerator} unit tests.
 */
public class GeoscalingIntegrationTest {

    protected static final Logger LOG = LoggerFactory.getLogger(GeoscalingIntegrationTest.class);

    private final String primaryDomain = "geopaas.org";//"domain"+((int)(Math.random()*10000))+".test.org";
    private final String subDomain = "subdomain"+((int)(Math.random()*10000));
    private final InetAddress addrWithGeo = NetworkUtils.getLocalHost();
    private final InetAddress addrWithoutGeo = NetworkUtils.getInetAddressWithFixedName(StubHostGeoLookup.HOMELESS_IP);
    
    private final SshMachineLocation locWithGeo = new SshMachineLocation(MutableMap.builder()
            .put("address", addrWithGeo)
            .put("name", "Edinburgh")
            .put("latitude", 55.94944)
            .put("longitude", -3.16028)
            .put("iso3166", ImmutableList.of("GB-EDH"))
            .build());

    private final SshMachineLocation locWithoutGeo = new SshMachineLocation(MutableMap.builder()
            .put("address", addrWithoutGeo)
            .put("name", "Nowhere")
            .build());

    private TestApplication app;
    private TestEntity target;
    private DynamicGroup group;
    private GeoscalingDnsService geoDns;
    private String geoLookupImpl;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        geoLookupImpl = BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getValue();
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        target = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        
        group = app.createAndManageChild(EntitySpecs.spec(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class)));
        
        geoDns = app.createAndManageChild(EntitySpecs.spec(GeoscalingDnsService.class)
                .displayName("Geo-DNS")
                .configure("username", "cloudsoft")
                .configure("password", "cl0uds0ft")
                .configure("primaryDomainName", primaryDomain)
                .configure("smartSubdomainName", subDomain)
                .configure("targetEntityProvider", group));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (geoLookupImpl != null) {
            System.setProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName(), geoLookupImpl);
        } else {
            System.clearProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName());
        }
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups={"Integration"})
    public void testRoutesToExpectedLocation() {
        target.setAttribute(Attributes.HOSTNAME,addrWithGeo.getHostName());
        
        app.start(ImmutableList.of(locWithGeo));
        
        LOG.info("geo-scaling test, using {}.{}; expect to be wired to {}", new Object[] {subDomain, primaryDomain, addrWithGeo});
        
        assertTargetHostsEventually(geoDns, 1);
    }
    
    @Test(groups={"Integration"})
    public void testIgnoresAddressWithoutGeography() {
        System.setProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName(), StubHostGeoLookup.class.getName());
        target.setAttribute(Attributes.HOSTNAME, StubHostGeoLookup.HOMELESS_IP);
        
        app.start(ImmutableList.of(locWithoutGeo));
        
        LOG.info("geo-scaling test, using {}.{}; expect to be wired to {}", new Object[] {subDomain, primaryDomain, addrWithoutGeo});
        
        Asserts.succeedsContinually(MutableMap.of("timeout", 10*1000), new Runnable() {
            @Override public void run() {
                assertEquals(geoDns.getTargetHosts().size(), 0, "targets="+geoDns.getTargetHosts());
            }
        });
    }

    @Test(groups={"Integration"})
    public void testIncludesAddressWithoutGeography() {
        System.setProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName(), StubHostGeoLookup.class.getName());
        ((EntityLocal)geoDns).setConfig(GeoscalingDnsService.INCLUDE_HOMELESS_ENTITIES, true);
        target.setAttribute(Attributes.HOSTNAME, StubHostGeoLookup.HOMELESS_IP);
        
        app.start(ImmutableList.of(locWithoutGeo));
        
        LOG.info("geo-scaling test, using {}.{}; expect to be wired to {}", new Object[] {subDomain, primaryDomain, addrWithoutGeo});
        
        assertTargetHostsEventually(geoDns, 1);
    }

    private void assertTargetHostsEventually(final GeoscalingDnsService geoDns, final int numExpected) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(geoDns.getTargetHosts().size(), 1, "targets="+geoDns.getTargetHosts());
            }
        });
    }
    
    public static class StubHostGeoLookup implements HostGeoLookup {
        public static final String HOMELESS_IP = "1.2.3.4";
        private final HostGeoLookup delegate;
        
        public StubHostGeoLookup(String delegateImpl) throws Exception {
            if (delegateImpl == null) {
                delegate = new MaxMindHostGeoLookup();
            } else {
                delegate = (HostGeoLookup) Class.forName(delegateImpl).newInstance();
            }
        }

        @Override
        public HostGeoInfo getHostGeoInfo(InetAddress address) throws Exception {
            if (HOMELESS_IP.equals(address.getHostAddress())) {
                return null;
            } else {
                return delegate.getHostGeoInfo(address);
            }
        }
    }
}
