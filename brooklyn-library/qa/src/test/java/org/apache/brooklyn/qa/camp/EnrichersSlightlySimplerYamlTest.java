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
package org.apache.brooklyn.qa.camp;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.math.MathPredicates;
import org.apache.brooklyn.util.text.StringPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

/** Tests some improvements to enricher classes to make them a bit more yaml friendly.
 * Called "SlightlySimpler" as it would be nice to make enrichers a lot more yaml friendly! */
@Test
public class EnrichersSlightlySimplerYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(EnrichersSlightlySimplerYamlTest.class);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testWithAppEnricher() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-app-with-enrichers-slightly-simpler.yaml"));
        waitForApplicationTasks(app);
        log.info("Started "+app+":");
        Entities.dumpInfo(app);
        
        Entity cluster = Iterables.getOnlyElement( app.getChildren() );
        Collection<Entity> leafs = ((DynamicCluster)cluster).getMembers();
        Iterator<Entity> li = leafs.iterator();
        
        Entity e1 = li.next();
        ((EntityInternal)e1).sensors().set(Sensors.newStringSensor("ip"), "127.0.0.1");
        EntityTestUtils.assertAttributeEqualsEventually(e1, Sensors.newStringSensor("url"), "http://127.0.0.1/");
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.MAIN_URI, URI.create("http://127.0.0.1/"));

        int i=2;
        while (li.hasNext()) {
            Entity ei = li.next();
            ((EntityInternal)ei).sensors().set(Sensors.newStringSensor("ip"), "127.0.0."+i);
            i++;
        }
        
        EntityTestUtils.assertAttributeEventually(cluster, Sensors.newSensor(Iterable.class, "urls.list"),
            (Predicate)CollectionFunctionals.sizeEquals(3));
        
        EntityTestUtils.assertAttributeEventually(cluster, Sensors.newSensor(String.class, "urls.list.comma_separated.max_2"),
            StringPredicates.matchesRegex("\"http:\\/\\/127[^\"]*\\/\",\"http:\\/\\/127[^\"]*\\/\""));

        EntityTestUtils.assertAttributeEventually(cluster, Attributes.MAIN_URI, Predicates.notNull());
        URI main = cluster.getAttribute(Attributes.MAIN_URI);
        Assert.assertTrue(main.toString().matches("http:\\/\\/127.0.0..\\/"), "Wrong URI: "+main);
        
        EntityTestUtils.assertAttributeEventually(app, Attributes.MAIN_URI, Predicates.notNull());
        main = app.getAttribute(Attributes.MAIN_URI);
        Assert.assertTrue(main.toString().matches("http:\\/\\/127.0.0..\\/"), "Wrong URI: "+main);
        
        // TODO would we want to allow "all-but-usual" as the default if nothing specified
    }
    
    @Test(groups="Integration")
    public void testWebappWithAveragingEnricher() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-webapp-with-averaging-enricher.yaml"));
        waitForApplicationTasks(app);
        log.info("Started "+app+":");
        Entities.dumpInfo(app);

        List<JavaWebAppSoftwareProcess> appservers = MutableList.copyOf(Entities.descendants(app, JavaWebAppSoftwareProcess.class));
        Assert.assertEquals(appservers.size(), 3);
        
        EntityInternal srv0 = (EntityInternal) appservers.get(0);
        EntityInternal dwac = (EntityInternal) srv0.getParent();
        EntityInternal cdwac = (EntityInternal) dwac.getParent();
        
        srv0.sensors().set(Sensors.newDoubleSensor("my.load"), 20.0);
        
        EntityTestUtils.assertAttributeEventually(dwac, Sensors.newSensor(Double.class, "my.load.averaged"),
            MathPredicates.equalsApproximately(20));
        EntityTestUtils.assertAttributeEventually(cdwac, Sensors.newSensor(Double.class, "my.load.averaged"),
            MathPredicates.equalsApproximately(20));

        srv0.sensors().set(Sensors.newDoubleSensor("my.load"), null);
        EntityTestUtils.assertAttributeEventually(cdwac, Sensors.newSensor(Double.class, "my.load.averaged"),
            Predicates.isNull());

        ((EntityInternal) appservers.get(1)).sensors().set(Sensors.newDoubleSensor("my.load"), 10.0);
        ((EntityInternal) appservers.get(2)).sensors().set(Sensors.newDoubleSensor("my.load"), 20.0);
        EntityTestUtils.assertAttributeEventually(cdwac, Sensors.newSensor(Double.class, "my.load.averaged"),
            MathPredicates.equalsApproximately(15));
        srv0.sensors().set(Sensors.newDoubleSensor("my.load"), 0.0);
        EntityTestUtils.assertAttributeEventually(cdwac, Sensors.newSensor(Double.class, "my.load.averaged"),
            MathPredicates.equalsApproximately(10));
    }
    
    @Override
    protected Logger getLogger() {
        return log;
    }
    
}
