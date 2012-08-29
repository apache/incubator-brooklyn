package brooklyn.entity.proxy;

import static org.testng.Assert.assertEquals;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.nginx.UrlMapping;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class UrlMappingTest {
    private TestApplication app;
    private UrlMapping urlMapping;
    
    @BeforeMethod
    public void setup() {
        app = new TestApplication();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) app.stop();
    }

    @Test(groups = "Integration")
    public void testTargetMappingsMatchesClusterMembers() {
        EntityFactory<StubAppServer> serverFactory = new BasicConfigurableEntityFactory<StubAppServer>(StubAppServer.class);
        final DynamicCluster cluster = new DynamicCluster(MutableMap.of("initialSize", 2, "factory", serverFactory), app);

        urlMapping = new UrlMapping(
                MutableMap.builder()
                        .put("domain", "localhost")
                        .put("target", cluster)
                        .build(),
                app);

        app.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
        
        // Check updates its TARGET_ADDRESSES (through async subscription)
        TestUtils.executeUntilSucceeds(new Runnable() {
            public void run() {
                Iterable<String> expectedTargets = Iterables.transform(cluster.getMembers(), new Function<Entity,String>() {
                        @Override public String apply(@Nullable Entity input) {
                            return input.getAttribute(Attributes.HOSTNAME)+":"+input.getAttribute(Attributes.HTTP_PORT);
                        }});
                
                assertEquals(urlMapping.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), 2);
                assertEquals(ImmutableSet.copyOf(urlMapping.getAttribute(UrlMapping.TARGET_ADDRESSES)), ImmutableSet.copyOf(expectedTargets));
            }});
    }
}
