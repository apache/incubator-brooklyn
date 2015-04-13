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

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.basic.Sensors;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.text.StringPredicates;

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
        ((EntityInternal)e1).setAttribute(Sensors.newStringSensor("ip"), "127.0.0.1");
        EntityTestUtils.assertAttributeEqualsEventually(e1, Sensors.newStringSensor("url"), "http://127.0.0.1/");
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.MAIN_URI, URI.create("http://127.0.0.1/"));

        int i=2;
        while (li.hasNext()) {
            Entity ei = li.next();
            ((EntityInternal)ei).setAttribute(Sensors.newStringSensor("ip"), "127.0.0."+i);
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
    
    @Override
    protected Logger getLogger() {
        return log;
    }
    
}
