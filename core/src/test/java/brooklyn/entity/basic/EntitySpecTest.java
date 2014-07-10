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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.Iterables;

public class EntitySpecTest extends BrooklynAppUnitTestSupport {

    private static final int TIMEOUT_MS = 10*1000;
    
    private SimulatedLocation loc;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = new SimulatedLocation();
    }
    
    @Test
    public void testSetsConfig() throws Exception {
        // TODO Test other permutations
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "myname"));
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "myname");
    }

    @Test
    public void testAddsChildren() throws Exception {
        entity = app.createAndManageChild( EntitySpec.create(TestEntity.class)
            .displayName("child")
            .child(EntitySpec.create(TestEntity.class)
                .displayName("grandchild")) );
        
        Entity child = Iterables.getOnlyElement(app.getChildren());
        assertEquals(child, entity);
        assertEquals(child.getDisplayName(), "child");
        Entity grandchild = Iterables.getOnlyElement(child.getChildren());
        assertEquals(grandchild.getDisplayName(), "grandchild");
    }
    

    @Test
    public void testAddsPolicySpec() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(PolicySpec.create(MyPolicy.class)
                        .displayName("mypolicyname")
                        .configure(MyPolicy.CONF1, "myconf1val")
                        .configure("myfield", "myfieldval")));
        
        Policy policy = Iterables.getOnlyElement(entity.getPolicies());
        assertTrue(policy instanceof MyPolicy, "policy="+policy);
        assertEquals(policy.getName(), "mypolicyname");
        assertEquals(policy.getConfig(MyPolicy.CONF1), "myconf1val");
    }
    
    @Test
    public void testAddsPolicy() throws Exception {
        MyPolicy policy = new MyPolicy();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(policy));
        
        assertEquals(Iterables.getOnlyElement(entity.getPolicies()), policy);
    }
    
    @Test
    public void testAddsEnricherSpec() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .enricher(EnricherSpec.create(MyEnricher.class)
                        .displayName("myenrichername")
                        .configure(MyEnricher.CONF1, "myconf1val")
                        .configure("myfield", "myfieldval")));
        
        Enricher enricher = Iterables.getOnlyElement(entity.getEnrichers());
        assertTrue(enricher instanceof MyEnricher, "enricher="+enricher);
        assertEquals(enricher.getName(), "myenrichername");
        assertEquals(enricher.getConfig(MyEnricher.CONF1), "myconf1val");
    }
    
    @Test
    public void testAddsEnricher() throws Exception {
        MyEnricher enricher = new MyEnricher();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .enricher(enricher));
        
        assertEquals(Iterables.getOnlyElement(entity.getEnrichers()), enricher);
    }
    
    public static class MyPolicy extends AbstractPolicy {
        public static final BasicConfigKey<String> CONF1 = new BasicConfigKey<String>(String.class, "testpolicy.conf1", "my descr, conf1", "defaultval1");
        public static final BasicConfigKey<Integer> CONF2 = new BasicConfigKey<Integer>(Integer.class, "testpolicy.conf2", "my descr, conf2", 2);
        
        @SetFromFlag
        public String myfield;
    }
    
    public static class MyEnricher extends AbstractEnricher {
        public static final BasicConfigKey<String> CONF1 = new BasicConfigKey<String>(String.class, "testenricher.conf1", "my descr, conf1", "defaultval1");
        public static final BasicConfigKey<Integer> CONF2 = new BasicConfigKey<Integer>(Integer.class, "testenricher.conf2", "my descr, conf2", 2);
        
        @SetFromFlag
        public String myfield;
    }
}
