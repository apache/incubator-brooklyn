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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.management.Task;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.text.StringPredicates;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

@Test
public class VanillaBashNetcatYamlTest extends AbstractYamlTest {

    private static final Logger log = LoggerFactory.getLogger(VanillaBashNetcatYamlTest.class);

    private static final AttributeSensor<String> SENSOR_OUTPUT_ALL = Sensors.newStringSensor("output.all");
    final static Effector<String> EFFECTOR_SAY_HI = Effectors.effector(String.class, "sayHiNetcat").buildAbstract();
    
    @Test(groups="Integration")
    public void testInvocationSensorAndEnricher() throws Exception {
        Preconditions.checkArgument(Networking.isPortAvailable(4321), "port 4321 must not be in use (no leaked nc instances) for this test to succeed!");
        
        Entity app = createAndStartApplication(loadYaml("vanilla-bash-netcat-w-client.yaml"));
        waitForApplicationTasks(app);
        
        log.info("App started:");
        Entities.dumpInfo(app);
        
        Assert.assertEquals(app.getDisplayName(), "Simple Netcat with Client");
        
        // comparing by plan ID is one common way
        Iterable<Entity> netcatI = Iterables.filter(app.getChildren(), EntityPredicates.configEqualTo(BrooklynCampConstants.PLAN_ID, "netcat-server"));
        Assert.assertTrue(netcatI.iterator().hasNext(), "no 'netcat-server' child of app: "+app.getChildren());
        Entity netcat = Iterables.getOnlyElement(netcatI);
        
        // make sure netcat is running
        EntityTestUtils.assertAttributeEventually(netcat, Attributes.SERVICE_STATE, Predicates.equalTo(Lifecycle.RUNNING));
        
        // find the pinger, now comparing by name
        Iterable<Entity> pingerI = Iterables.filter(app.getChildren(), EntityPredicates.displayNameEqualTo("Simple Pinger"));
        Assert.assertTrue(pingerI.iterator().hasNext(), "no 'Simple Pinger' child of app: "+app.getChildren());
        Entity pinger = Iterables.getOnlyElement(pingerI);

        // invoke effector
        Task<String> ping;
        ping = pinger.invoke(EFFECTOR_SAY_HI, MutableMap.<String,Object>of());
        Assert.assertEquals(ping.get().trim(), "hello");
        // and check we get the right result 
        EntityTestUtils.assertAttributeEventually(netcat, SENSOR_OUTPUT_ALL, StringPredicates.containsLiteral("hi netcat"));
        log.info("invoked ping from "+pinger+" to "+netcat+", 'all' sensor shows:\n"+
            netcat.getAttribute(SENSOR_OUTPUT_ALL));

        // netcat should now fail and restart
        EntityTestUtils.assertAttributeEventually(netcat, Attributes.SERVICE_STATE, Predicates.not(Predicates.equalTo(Lifecycle.RUNNING)));
        log.info("detected failure, state is: "+netcat.getAttribute(Attributes.SERVICE_STATE));
        EntityTestUtils.assertAttributeEventually(netcat, Attributes.SERVICE_STATE, Predicates.equalTo(Lifecycle.RUNNING));
        log.info("detected recovery, state is: "+netcat.getAttribute(Attributes.SERVICE_STATE));

        // invoke effector again, now with a parameter
        ping = pinger.invoke(EFFECTOR_SAY_HI, MutableMap.<String,Object>of("message", "yo yo yo"));
        Assert.assertEquals(ping.get().trim(), "hello");
        // checking right result
        EntityTestUtils.assertAttributeEventually(netcat, SENSOR_OUTPUT_ALL, StringPredicates.containsLiteral("yo yo yo"));
        log.info("invoked ping again from "+pinger+" to "+netcat+", 'all' sensor shows:\n"+
            netcat.getAttribute(SENSOR_OUTPUT_ALL));
        
        // and it's propagated to the app
        EntityTestUtils.assertAttributeEventually(app, Sensors.newStringSensor("output.last"), StringPredicates.containsLiteral("yo yo yo"));
        
        log.info("after all is said and done, app is:");
        Entities.dumpInfo(app);
    }
    
    @Override
    protected Logger getLogger() {
        return log;
    }
    
}
