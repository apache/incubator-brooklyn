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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.stream.Streams;

public class RebindEntityDynamicTypeInfoTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindEntityDynamicTypeInfoTest.class);
    
    @Override
    protected TestApplication rebind() throws Exception {
        return rebind(false);
    }
    
    public static class SayHiBody extends EffectorBody<String> {
        public static final ConfigKey<String> NAME_KEY = ConfigKeys.newStringConfigKey("name");
        public static final Effector<String> EFFECTOR = Effectors.effector(String.class, "say_hi").description("says hello")
            .parameter(NAME_KEY).impl(new SayHiBody()).build();
        
        @Override
        public String call(ConfigBag parameters) {
            return "hello "+parameters.get(NAME_KEY);
        }
    }
    
    @Test
    public void testRestoresEffectorStaticClass() throws Exception {
        origApp.getMutableEntityType().addEffector(SayHiBody.EFFECTOR);
        checkEffectorWithRebind();
    }
    
    @Test
    public void testMementoNotTooBig() throws Exception {
        origApp.addChild(EntitySpec.create(TestEntity.class));
        
        // dynamic conf key
        origApp.setConfig(TestEntity.CONF_NAME, "slim");
        // declared sensor
        origApp.setAttribute(TestApplication.MY_ATTRIBUTE, "foo");
        // dynamic sensor
        origApp.setAttribute(TestEntity.SEQUENCE, 98765);
        
        // dynamic effector
        origApp.getMutableEntityType().addEffector(SayHiBody.EFFECTOR);
        
        RebindTestUtils.waitForPersisted(origApp);
        
        File mementoFile = new File(new File(mementoDir, "entities"), origApp.getId());
        String memento = Streams.readFully(new FileReader(mementoFile));
        log.info("memento is:\n"+memento);
        // make sure it's not too long, and doesn't have declared items
        Assert.assertTrue(memento.length() < 4000, "length is: "+memento.length());
        Assert.assertFalse(memento.contains("restart"));
        Assert.assertFalse(memento.contains(TestApplication.MY_ATTRIBUTE.getDescription()));
        Assert.assertFalse(memento.toLowerCase().contains("typetoken"));
    }

    // does not work, as the class is anonymous not static so pulls in too much stuff from the test fixture
    // (including e.g. mgmt context and non-serializable guice bindings)
    @Test(enabled=false)
    public void testRestoresEffectorAnonymousClass() throws Exception {
        origApp.getMutableEntityType().addEffector(Effectors.effector(String.class, "say_hi")
            .parameter(SayHiBody.NAME_KEY)
            .description("says hello")
            .impl(new EffectorBody<String>() {
                @Override
                public String call(ConfigBag parameters) {
                    return "hello "+parameters.get(SayHiBody.NAME_KEY);
                }
            }).build());
        checkEffectorWithRebind();
        
    }

    private void checkEffectorWithRebind() throws InterruptedException, ExecutionException,
        Exception {
        Effector<?> eff = origApp.getEntityType().getEffectorByName("say_hi").get();
        assertEquals(origApp.invoke(eff, ConfigBag.newInstance().configure(SayHiBody.NAME_KEY, "bob").getAllConfig()).get(), "hello bob");
        
        newApp = rebind();
        log.info("Effectors on new app: "+newApp.getEntityType().getEffectors());
        assertNotSame(newApp, origApp);
        assertEquals(newApp.getId(), origApp.getId());
        
        assertEquals(newApp.invoke(eff, ConfigBag.newInstance().configure(SayHiBody.NAME_KEY, "bob").getAllConfig()).get(), "hello bob");
        eff = newApp.getEntityType().getEffectorByName("say_hi").get();
        assertEquals(newApp.invoke(eff, ConfigBag.newInstance().configure(SayHiBody.NAME_KEY, "bob").getAllConfig()).get(), "hello bob");
    }
    
}
