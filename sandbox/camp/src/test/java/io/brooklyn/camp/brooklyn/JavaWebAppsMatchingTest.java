package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.rest.util.CampJsons;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

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
import brooklyn.util.collections.MutableMap;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DeferredSupplier;

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

    public void testExampleFunctionsYamlMatch() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("example-with-function.yaml"));
        
        DeploymentPlan plan = platform.pdp().parseDeploymentPlan(input);
        log.info("DP is:\n"+plan.toString());
        Map<?,?> cfg1 = (Map<?, ?>) plan.getServices().get(0).getCustomAttributes().get("brooklyn.config");
        Map<?,?> cfg = MutableMap.copyOf(cfg1);
        
        Assert.assertEquals(cfg.remove("literalValue1"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("literalValue2"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("literalValue3"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("literalValue4"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("$brooklyn:1"), "key to the city");
        Assert.assertTrue(cfg.isEmpty(), ""+cfg);

        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(plan);
        
        log.info("AT is:\n"+CampJsons.prettyJson(new CampServer(platform, "").getDtoFactory().adapt(at)));
        Assert.assertEquals(at.getName(), "example-with-function");
        
        PlatformComponentTemplate pct = at.getPlatformComponentTemplates().links().iterator().next().resolve();
        log.info("PCT is:\n"+CampJsons.prettyJson(new CampServer(platform, "").getDtoFactory().adapt(pct)));
        Object cfg2 = pct.getCustomAttributes().get("brooklyn.config");
        Assert.assertEquals(cfg2, cfg1);
    }

    public void testJavaAndDbWithFunctionYamlMatch() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-and-db-with-function.yaml"));
        assertWebDbWithFunctionValid(input);
    }
    
    public void testJavaAndDbWithFunctionYamlMatch2() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-and-db-with-function-2.yaml"));
        assertWebDbWithFunctionValid(input);
    }
    
    protected void assertWebDbWithFunctionValid(Reader input) { 
        DeploymentPlan plan = platform.pdp().parseDeploymentPlan(input);
        log.info("DP is:\n"+plan.toString());
        
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(plan);
        
        log.info("AT is:\n"+CampJsons.prettyJson(new CampServer(platform, "").getDtoFactory().adapt(at)));
        Assert.assertEquals(at.getName(), "java-cluster-db-example");

        Iterator<ResolvableLink<PlatformComponentTemplate>> pcti = at.getPlatformComponentTemplates().links().iterator();
        PlatformComponentTemplate pct1 = pcti.next().resolve(); 
        log.info("PCT1 is:\n"+CampJsons.prettyJson(new CampServer(platform, "").getDtoFactory().adapt(pct1)));

        PlatformComponentTemplate pct2 = pcti.next().resolve(); 
        log.info("PCT2 is:\n"+CampJsons.prettyJson(new CampServer(platform, "").getDtoFactory().adapt(pct2)));

        Map<?,?> config = (Map<?, ?>) pct1.getCustomAttributes().get("brooklyn.config");
        Map<?,?> javaSysProps = (Map<?, ?>) config.get("java.sysprops");
        Object dbUrl = javaSysProps.get("brooklyn.example.db.url");
        String j = CampJsons.prettyJson(dbUrl);
        Assert.assertTrue(dbUrl instanceof DeferredSupplier<?>, "url is: "+dbUrl);
        Assert.assertTrue(j.indexOf("formatString") >= 0, "url json is: "+j);
        
        Assert.assertEquals(pct2.getId(), "db");
    }

}
