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

import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.stream.Streams;

@Test
public class EmptySoftwareProcessYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(EnrichersYamlTest.class);

    @Test
    public void testProvisioningProperties() throws Exception {
        Entity app = createAndStartApplication(Streams.newReaderWithContents(
            "services: [ { serviceType: "+EmptySoftwareProcess.class.getName()+","
                + " provisioning.properties: { minRam: 16384 } } ]"));
        waitForApplicationTasks(app);

        log.info("App started:");
        Entities.dumpInfo(app);
        
        EmptySoftwareProcess entity = (EmptySoftwareProcess) app.getChildren().iterator().next();
        Map<String, Object> pp = entity.getConfig(EmptySoftwareProcess.PROVISIONING_PROPERTIES);
        Assert.assertEquals(pp.get("minRam"), 16384);
    }

    @Test
    public void testProvisioningPropertiesViaJsonya() throws Exception {
        Entity app = createAndStartApplication(Streams.newReaderWithContents(
            Jsonya.newInstance().at("services").list()
                .put("serviceType", EmptySoftwareProcess.class.getName())
                .at("provisioning.properties").put("minRam", 16384)
                .root().toString()
        ));
        waitForApplicationTasks(app);

        log.info("App started:");
        Entities.dumpInfo(app);
        
        EmptySoftwareProcess entity = (EmptySoftwareProcess) app.getChildren().iterator().next();
        Map<String, Object> pp = entity.getConfig(EmptySoftwareProcess.PROVISIONING_PROPERTIES);
        Assert.assertEquals(pp.get("minRam"), 16384);
    }

    @Test
    // for issue #1377
    public void testWithAppAndEntityLocations() throws Exception {
        Entity app = createAndStartApplication(Streams.newReaderWithContents("services:\n"+
            "- serviceType: "+EmptySoftwareProcess.class.getName()+"\n"+
            "  location: localhost:(name=localhost on entity)"+"\n"+
            "location: byon:(hosts=\"127.0.0.1\", name=loopback on app)"));
        waitForApplicationTasks(app);
        Entities.dumpInfo(app);
        
        Assert.assertEquals(app.getLocations().size(), 1);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity entity = app.getChildren().iterator().next();
        
        Location appLocation = app.getLocations().iterator().next();
        Assert.assertEquals(appLocation.getDisplayName(), "loopback on app");
        
        Assert.assertEquals(entity.getLocations().size(), 2);
        Iterator<Location> entityLocationIterator = entity.getLocations().iterator();
        Assert.assertEquals(entityLocationIterator.next().getDisplayName(), "localhost on entity");
        Location actualMachine = entityLocationIterator.next();
        Assert.assertTrue(actualMachine instanceof SshMachineLocation, "wrong location: "+actualMachine);
        // TODO this, below, probably should be 'localhost on entity', see #1377
        Assert.assertEquals(actualMachine.getParent().getDisplayName(), "loopback on app");
    }

}
