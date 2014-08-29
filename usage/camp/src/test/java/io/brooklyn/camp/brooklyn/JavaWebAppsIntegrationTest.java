/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTaskTags;
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
import brooklyn.test.EntityTestUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Duration;

import com.google.common.collect.Iterables;

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

            Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
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
            
            Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
            log.info("Waiting on "+tasks.size()+" task(s)");
            AtomicInteger i = new AtomicInteger(0);
            for (Task<?> t: tasks) {
                t.blockUntilEnded();
                log.info("Completed task #" + i.incrementAndGet());
            }

            log.info("App started:");
            Entities.dumpInfo(app);

            EntityTestUtils.assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
            Assert.assertEquals(app.getAttribute(Attributes.SERVICE_UP), Boolean.TRUE);
            
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
            
            String dbPage = new ResourceUtils(this).getResourceAsString(Urls.mergePaths(url, "db.jsp"));
            Assert.assertTrue(dbPage.contains("Isaac Asimov"), "db.jsp does not mention Isaac Asimov, probably the DB did not get initialised:\n"+dbPage);
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
            
            Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
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

            EntityTestUtils.assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
            Assert.assertEquals(app.getAttribute(Attributes.SERVICE_UP), Boolean.TRUE);
            
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
            
            String dbPage = new ResourceUtils(this).getResourceAsString(Urls.mergePaths(url, "db.jsp"));
            Assert.assertTrue(dbPage.contains("Isaac Asimov"), "db.jsp does not mention Isaac Asimov, probably the DB did not get initialised:\n"+dbPage);
        } catch (Exception e) {
            log.warn("Unable to instantiate "+at+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }
    
}
