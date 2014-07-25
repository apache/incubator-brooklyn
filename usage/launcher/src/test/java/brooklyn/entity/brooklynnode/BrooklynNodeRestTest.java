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
package brooklyn.entity.brooklynnode;

import java.net.URI;
import java.util.concurrent.Callable;

import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.launcher.SimpleYamlLauncherForTests;
import brooklyn.launcher.camp.SimpleYamlLauncher;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.Urls;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.time.Duration;

import com.google.common.collect.Iterables;

/** REST-accessible extension of {@link BrooklynNodeTest} */
public class BrooklynNodeRestTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeRestTest.class);
    
    // takes a while when run on its own, because initializing war and making some requests;
    // but there are no waits (beyond 10ms), the delay is all classloading;
    // and this tests a lot of things, REST API, Brooklyn Node, yaml deployment,
    // so feels worth it to have as a unit test
    // FIXME[BROOKLYN-43]: Test fails if security is configured in brooklyn.properties.
    @Test(groups = "WIP")
    public void testBrooklynNodeRestDeployAndMirror() {
        final SimpleYamlLauncher l = new SimpleYamlLauncherForTests();
        try {
            TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, l.getManagementContext());

            BrooklynNode bn = app.createAndManageChild(EntitySpec.create(BrooklynNode.class, SameBrooklynNodeImpl.class));
            bn.start(MutableSet.<Location>of());
            
            URI uri = bn.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
            Assert.assertNotNull(uri);
            EntityTestUtils.assertAttributeEqualsEventually(bn, Attributes.SERVICE_UP, true);
            log.info("Created BrooklynNode: "+bn);

            // deploy
            Task<?> t = bn.invoke(BrooklynNode.DEPLOY_BLUEPRINT, ConfigBag.newInstance()
                .configure(BrooklynNode.DeployBlueprintEffector.BLUEPRINT_TYPE, TestApplication.class.getName())
                .configure(BrooklynNode.DeployBlueprintEffector.BLUEPRINT_CONFIG, MutableMap.<String,Object>of("x", 1, "y", "Y"))
                .getAllConfig());
            log.info("Deployment result: "+t.getUnchecked());
            
            MutableSet<Application> apps = MutableSet.copyOf( l.getManagementContext().getApplications() );
            Assert.assertEquals(apps.size(), 2);
            apps.remove(app);
            
            Application newApp = Iterables.getOnlyElement(apps);
            Entities.dumpInfo(newApp);
            
            Assert.assertEquals(newApp.getConfig(new BasicConfigKey<Integer>(Integer.class, "x")), (Integer)1);
            
            // check mirror
            String newAppId = newApp.getId();
            BrooklynEntityMirror mirror = app.createAndManageChild(EntitySpec.create(BrooklynEntityMirror.class)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_URL, 
                    Urls.mergePaths(uri.toString(), "/v1/applications/"+newAppId+"/entities/"+newAppId))
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_ID, newAppId)
                .configure(BrooklynEntityMirror.POLL_PERIOD, Duration.millis(10)));
            
            Entities.dumpInfo(mirror);
            
            EntityTestUtils.assertAttributeEqualsEventually(mirror, Attributes.SERVICE_UP, true);
            
            ((EntityInternal)newApp).setAttribute(TestEntity.NAME, "foo");
            EntityTestUtils.assertAttributeEqualsEventually(mirror, TestEntity.NAME, "foo");
            log.info("Mirror successfully validated");
            
            // also try deploying by invoking deploy through json
            // (catch issues when effector params are map)
            HttpClient client = HttpTool.httpClientBuilder().build();
            HttpToolResponse result = HttpTool.httpPost(client, URI.create(Urls.mergePaths(uri.toString(), "/v1/applications/"+app.getId()+"/entities/"+bn.getId()
                    +"/effectors/deployBlueprint")), 
                MutableMap.of(com.google.common.net.HttpHeaders.CONTENT_TYPE, "application/json"), 
                Jsonya.newInstance()
                    .put("blueprintType", TestApplication.class.getName())
                    .put("blueprintConfig", MutableMap.of(TestEntity.CONF_NAME.getName(), "foo"))
                .toString().getBytes());
            log.info("Deploy effector invoked, result: "+result);
            HttpTestUtils.assertHealthyStatusCode( result.getResponseCode() );
            
            Repeater.create().every(Duration.millis(10)).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return l.getManagementContext().getApplications().size() == 3;
                }
            }).limitTimeTo(Duration.TEN_SECONDS);
            
            apps = MutableSet.copyOf( l.getManagementContext().getApplications() );
            apps.removeAll( MutableSet.of(app, newApp) );
            Application newApp2 = Iterables.getOnlyElement(apps);
            Entities.dumpInfo(newApp2);
            
            EntityTestUtils.assertAttributeEqualsEventually(newApp2, Attributes.SERVICE_UP, true);
            Assert.assertEquals(newApp2.getConfig(TestEntity.CONF_NAME), "foo");
            
        } finally {
            l.destroyAll();
        }
        log.info("DONE");
    }
}
