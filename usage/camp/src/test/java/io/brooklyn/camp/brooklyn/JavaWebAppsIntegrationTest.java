package io.brooklyn.camp.brooklyn;

import static com.google.common.base.Preconditions.checkNotNull;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.test.Asserts;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Duration;

@Test(groups="Integration")
public class JavaWebAppsIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(JavaWebAppsIntegrationTest.class);
    
    private ManagementContext brooklynMgmt;
    private BrooklynCampPlatform platform;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        BrooklynCampPlatformLauncherNoServer launcher = new BrooklynCampPlatformLauncherNoServer();
        launcher.launch();
        brooklynMgmt = launcher.getBrooklynMgmt();
      
        platform = new BrooklynCampPlatform(
              PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
              brooklynMgmt);
    }
    
    @AfterMethod
    public void teardown() {
        if (brooklynMgmt!=null) Entities.destroyAll(brooklynMgmt);
    }
    
    public void testSimpleYamlDeploy() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-simple.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

        try {
            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
            log.info("Test - created "+assembly);
            
            final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            log.info("App - "+app);
            Assert.assertEquals(app.getDisplayName(), "sample-single-jboss");
                        
            // locations set on AT in this yaml
            Assert.assertEquals(app.getLocations().size(), 1);

            Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
            log.info("Waiting on "+tasks.size()+" task(s)");
            for (Task<?> t: tasks) {
                t.blockUntilEnded();
            }

            log.info("App started:");
            Entities.dumpInfo(app);

            Assert.assertEquals(app.getChildren().size(), 1);
            Assert.assertEquals(app.getChildren().iterator().next().getDisplayName(), "jboss1");
            Assert.assertEquals(app.getChildren().iterator().next().getLocations().size(), 1);
            
            final String url = Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TEN_SECONDS), new Callable<String>() {
                @Override public String call() throws Exception {
                    String url = app.getChildren().iterator().next().getAttribute(JavaWebAppService.ROOT_URL);
                    return checkNotNull(url, "url of %s", app);
                }});
        
            String site = Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TEN_SECONDS), new Callable<String>() {
                    @Override public String call() throws Exception {
                        return new ResourceUtils(this).getResourceAsString(url);
                    }});
            
            log.info("App URL for "+app+": "+url);
            Assert.assertTrue(url.contains("928"), "URL should be on port 9280+ based on config set in yaml, url "+url+", app "+app);
            Assert.assertTrue(site.toLowerCase().contains("hello"), site);
            Assert.assertTrue(!platform.assemblies().isEmpty());
        } catch (Exception e) {
            log.warn("Unable to instantiate "+at+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }

    public void testWithDbDeploy() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-and-db-with-function.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

        try {
            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
            log.info("Test - created "+assembly);
            
            final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            log.info("App - "+app);
            
            // locations set on individual services here
            Assert.assertEquals(app.getLocations().size(), 0);
            
            Iterator<ResolvableLink<PlatformComponent>> pcs = assembly.getPlatformComponents().links().iterator();
            PlatformComponent pc1 = pcs.next().resolve();
            Entity cluster = brooklynMgmt.getEntityManager().getEntity(pc1.getId());
            log.info("pc1 - "+pc1+" - "+cluster);
            
            PlatformComponent pc2 = pcs.next().resolve();
            log.info("pc2 - "+pc2);
            
            Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
            log.info("Waiting on "+tasks.size()+" task(s)");
            for (Task<?> t: tasks) {
                t.blockUntilEnded();
            }

            log.info("App started:");
            Entities.dumpInfo(app);

            Assert.assertEquals(Lifecycle.RUNNING, app.getAttribute(Attributes.SERVICE_STATE));
            Assert.assertEquals(Boolean.TRUE, app.getAttribute(Attributes.SERVICE_UP));
            
            final String url = Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TEN_SECONDS), new Callable<String>() {
                    @Override public String call() throws Exception {
                        Entity cluster = Iterables.getOnlyElement( Iterables.filter(app.getChildren(), WebAppService.class) );
                        String url = cluster.getAttribute(JavaWebAppService.ROOT_URL);
                        return checkNotNull(url, "url of %s", cluster);
                    }});
            
            String site = Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TEN_SECONDS), new Callable<String>() {
                    @Override public String call() throws Exception {
                        return new ResourceUtils(this).getResourceAsString(url);
                    }});
            
            log.info("App URL for "+app+": "+url);
            Assert.assertTrue(url.contains("921"), "URL should be on port 9280+ based on config set in yaml, url "+url+", app "+app);
            Assert.assertTrue(site.toLowerCase().contains("hello"), site);
            Assert.assertTrue(!platform.assemblies().isEmpty());
            
            // TODO assert DB is working (send visitors command, and get result)
        } catch (Exception e) {
            log.warn("Unable to instantiate "+at+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }

    public void testWithPolicyDeploy() {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-and-db-with-policy.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

        try {
            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
            log.info("Test - created "+assembly);
            
            final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            log.info("App - "+app);
            
            // locations set on individual services here
            Assert.assertEquals(app.getLocations().size(), 0);
            
            Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
            log.info("Waiting on "+tasks.size()+" task(s)");
            for (Task<?> t: tasks) {
                t.blockUntilEnded();
            }
            
            log.info("App started:");
            Entities.dumpInfo(app);
            
            Iterator<ResolvableLink<PlatformComponent>> pcs = assembly.getPlatformComponents().links().iterator();
            PlatformComponent clusterComponent = null;
            while (pcs.hasNext() && clusterComponent == null) {
                PlatformComponent component = pcs.next().resolve();
                if (component.getName().equals("My Web with Policy"))
                    clusterComponent = component;
            }
            Assert.assertNotNull(clusterComponent, "Database PlatformComponent not found");
            Entity cluster = brooklynMgmt.getEntityManager().getEntity(clusterComponent.getId());
            log.info("pc1 - "+clusterComponent+" - "+cluster);
            
            Assert.assertEquals(cluster.getPolicies().size(), 1);
            Policy policy = cluster.getPolicies().iterator().next();
            Assert.assertNotNull(policy);
            Assert.assertTrue(policy instanceof AutoScalerPolicy, "policy="+policy);
            Assert.assertEquals(policy.getConfig(AutoScalerPolicy.MAX_POOL_SIZE), (Integer)5);
            Assert.assertEquals(policy.getConfig(AutoScalerPolicy.MIN_POOL_SIZE), (Integer)1);
            Assert.assertEquals(policy.getConfig(AutoScalerPolicy.METRIC), DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE);
            Assert.assertEquals(policy.getConfig(AutoScalerPolicy.METRIC_LOWER_BOUND), (Integer)10);
            Assert.assertEquals(policy.getConfig(AutoScalerPolicy.METRIC_UPPER_BOUND), (Integer)100);
            Assert.assertTrue(policy.isRunning());

            Assert.assertEquals(Lifecycle.RUNNING, app.getAttribute(Attributes.SERVICE_STATE));
            Assert.assertEquals(Boolean.TRUE, app.getAttribute(Attributes.SERVICE_UP));
            
            final String url = Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TEN_SECONDS), new Callable<String>() {
                    @Override public String call() throws Exception {
                        Entity cluster = Iterables.getOnlyElement( Iterables.filter(app.getChildren(), WebAppService.class) );
                        String url = cluster.getAttribute(JavaWebAppService.ROOT_URL);
                        return checkNotNull(url, "url of %s", cluster);
                    }});
            
            String site = Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TEN_SECONDS), new Callable<String>() {
                    @Override public String call() throws Exception {
                        return new ResourceUtils(this).getResourceAsString(url);
                    }});
            
            log.info("App URL for "+app+": "+url);
            Assert.assertTrue(url.contains("921"), "URL should be on port 9280+ based on config set in yaml, url "+url+", app "+app);
            Assert.assertTrue(site.toLowerCase().contains("hello"), site);
            Assert.assertTrue(!platform.assemblies().isEmpty());
        } catch (Exception e) {
            log.warn("Unable to instantiate "+at+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }
    
}
