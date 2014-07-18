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

import static org.testng.Assert.assertTrue;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

public class BrooklynNodeTest {

    // TODO Need test for copying/setting classpath
    
    private TestApplication app;
    private SshMachineLocation loc;

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
        String version = "0.6.0-SNAPSHOT";
        String expectedUrl = "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v="+version+"&a=brooklyn-dist&c=dist&e=tar.gz";
        runTestGeneratesCorrectDownloadUrl(version, expectedUrl);
    }
    
    @Test
    public void testGeneratesCorrectReleaseDownload() throws Exception {
        String version = "0.5.0";
        String expectedUrl = "http://search.maven.org/remotecontent?filepath=io/brooklyn/brooklyn-dist/"+version+"/brooklyn-dist-"+version+"-dist.tar.gz";
        runTestGeneratesCorrectDownloadUrl(version, expectedUrl);
    }
    
    private void runTestGeneratesCorrectDownloadUrl(String version, String expectedUrl) throws Exception {
        BrooklynNodeImpl entity = new BrooklynNodeImpl();
        entity.configure(MutableMap.of("version", version));
        entity.setParent(app);
        Entities.manage(entity);
        ConfigToAttributes.apply(entity);
        BrooklynNodeSshDriver driver = new BrooklynNodeSshDriver(entity, loc);
        
        DownloadResolver resolver = Entities.newDownloader(driver);
        List<String> urls = resolver.getTargets();
        
        System.out.println("urls="+urls);
        assertTrue(urls.contains(expectedUrl), "urls="+urls);
    }
    
    @Test
    public void testCanStartSameNode() throws Exception {
        // not very interesting as done not have REST when run in this project
        // but test BrooklynNodeRestTest in downstream project does
        BrooklynNode bn = app.createAndManageChild(EntitySpec.create(BrooklynNode.class, SameBrooklynNodeImpl.class));
        bn.start(MutableSet.<Location>of());
        
        Assert.assertEquals(bn.getAttribute(Attributes.SERVICE_UP), (Boolean)true);
        // no URI
        Assert.assertNull(bn.getAttribute(BrooklynNode.WEB_CONSOLE_URI));
    }

}
