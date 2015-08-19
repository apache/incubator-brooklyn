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
package org.apache.brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.drivers.downloads.DownloadResolver;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNodeImpl;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNodeSshDriver;
import org.apache.brooklyn.sensor.feed.ConfigToAttributes;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.location.ssh.SshMachineLocation;

public class BrooklynNodeTest {

    // TODO Need test for copying/setting classpath
    
    private TestApplication app;
    private SshMachineLocation loc;
    
    public static class SlowStopBrooklynNode extends BrooklynNodeImpl {
        public SlowStopBrooklynNode() {}
        
        @Override
        protected void postStop() {
            super.postStop();
            
            //Make sure UnmanageTask will wait for the STOP effector to complete.
            Time.sleep(Duration.FIVE_SECONDS);
        }
        
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc = new SshMachineLocation(MutableMap.of("address", "localhost"));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testGeneratesCorrectSnapshotDownload() throws Exception {
        String version = "0.0.1-SNAPSHOT";
        String expectedUrl = "https://repository.apache.org/service/local/artifact/maven/redirect?r=snapshots&g=org.apache.brooklyn&v="+version+"&a=brooklyn-dist&c=dist&e=tar.gz";
        runTestGeneratesCorrectDownloadUrl(version, expectedUrl);
    }
    
    @Test
    public void testGeneratesCorrectReleaseDownload() throws Exception {
        String version = "0.0.1";
        String expectedUrl = "http://search.maven.org/remotecontent?filepath=org/apache/brooklyn/brooklyn-dist/"+version+"/brooklyn-dist-"+version+"-dist.tar.gz";
        runTestGeneratesCorrectDownloadUrl(version, expectedUrl);
    }
    
    private void runTestGeneratesCorrectDownloadUrl(String version, String expectedUrl) throws Exception {
        // TODO Using BrooklynNodeImpl directly, because want to instantiate a BroolynNodeSshDriver.
        //      Really want to make that easier to test, without going through "wrong" code path for creating entity.
        BrooklynNodeImpl entity = new BrooklynNodeImpl();
        entity.setConfig(BrooklynNode.SUGGESTED_VERSION, version);
        entity.setParent(app);
        Entities.manage(entity);
        ConfigToAttributes.apply(entity);
        BrooklynNodeSshDriver driver = new BrooklynNodeSshDriver(entity, loc);
        
        DownloadResolver resolver = Entities.newDownloader(driver);
        List<String> urls = resolver.getTargets();
        
        System.out.println("urls="+urls);
        assertTrue(urls.contains(expectedUrl), "urls="+urls);
    }
    
    @Test(groups = "Integration")
    public void testUnmanageOnStop() throws Exception {
        final BrooklynNode node = app.addChild(EntitySpec.create(BrooklynNode.class).impl(SlowStopBrooklynNode.class));
        Entities.manage(node);
        assertTrue(Entities.isManaged(node), "Entity " + node + " must be managed.");
        node.invoke(Startable.STOP, ImmutableMap.<String,Object>of()).asTask().getUnchecked();
        //The UnmanageTask will unblock after the STOP effector completes, so we are competing with it here.
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertFalse(Entities.isManaged(node));
            }
        });
    }
    

    @Test
    public void testCanStartSameNode() throws Exception {
        // not very interesting as do not have REST when run in this project
        // but test BrooklynNodeRestTest in downstream project does
        BrooklynNode bn = app.createAndManageChild(EntitySpec.create(BrooklynNode.class, SameBrooklynNodeImpl.class));
        bn.start(MutableSet.<Location>of());
        
        Assert.assertEquals(bn.getAttribute(Attributes.SERVICE_UP), (Boolean)true);
        // no URI
        Assert.assertNull(bn.getAttribute(BrooklynNode.WEB_CONSOLE_URI));
    }

}
