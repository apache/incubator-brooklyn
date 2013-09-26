package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.rest.util.CampJsons;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;

import java.io.IOException;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.stream.Streams;

@Test
public class JavaWebAppsMatchingTest {

    private static final Logger log = LoggerFactory.getLogger(JavaWebAppsMatchingTest.class);
    
    private ManagementContext brooklynMgmt;
    private BrooklynCampPlatform platform;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        brooklynMgmt = new LocalManagementContext();
        platform = new BrooklynCampPlatform(
              PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
              brooklynMgmt);
    }
    
    @AfterMethod
    public void teardown() {
        if (brooklynMgmt!=null) Entities.destroyAll(brooklynMgmt);
    }
    
    public void testSimpleYamlParse() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-simple.yaml"));
        DeploymentPlan plan = platform.pdp().parseDeploymentPlan(input);
        log.info("DP is:\n"+plan.toString());
        Assert.assertEquals(plan.getServices().size(), 1);
        Assert.assertEquals(plan.getName(), "sample-single-jboss");
    }
    
    public void testSimpleYamlMatch() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-simple.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);
        
        log.info("AT is:\n"+CampJsons.prettyJson(new CampServer(platform, "").getDtoFactory().adapt(at)));
        
        Assert.assertEquals(at.getName(), "sample-single-jboss");
    }

}
