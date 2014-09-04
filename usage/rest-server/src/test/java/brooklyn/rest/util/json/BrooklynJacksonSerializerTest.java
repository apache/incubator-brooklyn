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
package brooklyn.rest.util.json;

import java.io.IOException;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynRestApiLauncher;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.exceptions.Exceptions;

import com.google.gson.Gson;

public class BrooklynJacksonSerializerTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynJacksonSerializerTest.class);
    
    public static class SillyClassWithManagementContext {
        @JsonProperty
        ManagementContext mgmt;
        @JsonProperty
        String id;
        
        public SillyClassWithManagementContext() { }
        
        public SillyClassWithManagementContext(String id, ManagementContext mgmt) {
            this.id = id;
            this.mgmt = mgmt;
        }

        @Override
        public String toString() {
            return super.toString()+"[id="+id+";mgmt="+mgmt+"]";
        }
    }

    @Test
    public void testCustomSerializerWithSerializableSillyManagementExample() throws JsonGenerationException, JsonMappingException, IOException {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        try {

            ObjectMapper mapper = BrooklynJacksonJsonProvider.newPrivateObjectMapper(mgmt);

            SillyClassWithManagementContext silly = new SillyClassWithManagementContext("123", mgmt);
            log.info("silly is: "+silly);

            String sillyS = mapper.writeValueAsString(silly);

            log.info("silly json is: "+sillyS);

            SillyClassWithManagementContext silly2 = mapper.readValue(sillyS, SillyClassWithManagementContext.class);
            log.info("silly2 is: "+silly2);

            Assert.assertEquals(silly.id, silly2.id);
            
        } finally {
            Entities.destroyAll(mgmt);
        }
    }
    
    public static class AngrySelfRefNonSerializableClass {
        @JsonProperty
        Object bogus = this;
    }

    @Test
    public void testCustomSerializerWithNonSerializableAngrySelfRef() {
        checkNonSerializable(new AngrySelfRefNonSerializableClass());
    }

    public static class AngryEmptyNonSerializableClass {
    }

    @Test
    public void testCustomSerializerWithNonSerializableAngryEmpty() {
        checkNonSerializable(new AngryEmptyNonSerializableClass());
    }

    // TODO would like to find an example which is non-serializable because of infinite recursion,
    // to test StackOverflowError -- tested here and in the Integration test below.
    // (but I can't seem to make one!)
    
    protected void checkNonSerializable(Object x) {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        try {
            ObjectMapper mapper = BrooklynJacksonJsonProvider.newPrivateObjectMapper(mgmt);

            String tS = mapper.writeValueAsString(x);

            Assert.fail("Should not have serialized "+x+"; instead gave: "+tS);
            
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.info("Got expected error, when serializing "+x+": "+e);
            
        } finally {
            Entities.destroyAll(mgmt);
        }
    }
    
    @Test(groups="Integration") //because of time
    public void testWithLauncherSerializingListsContainingEntitiesAndOtherComplexStuff() {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        Server server = null;
        try {
            server = BrooklynRestApiLauncher.launcher().managementContext(mgmt).start();
            
            TestApplication app = TestApplication.Factory.newManagedInstanceForTests(mgmt);

            String appUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort()+"/v1/applications/"+app.getId();
            String entityUrl = appUrl + "/entities/"+app.getId();

            app.setConfig(TestEntity.CONF_OBJECT, mgmt);
            String content = HttpTestUtils.getContent(entityUrl+"/config/"+TestEntity.CONF_OBJECT.getName());

            // assert config here is just mgmt
            log.info("CONFIG MGMT is:\n"+content);
            @SuppressWarnings("rawtypes")
            Map values = new Gson().fromJson(content, Map.class);
            Assert.assertEquals(values.size(), 1, "Map is wrong size: "+values);
            Assert.assertEquals(values.get("type"), LocalManagementContextForTests.class.getCanonicalName());
            
            // assert normal API returns the same, containing links
            log.info("ENTITY is: \n"+content);
            content = HttpTestUtils.getContent(entityUrl);
            values = new Gson().fromJson(content, Map.class);
            Assert.assertTrue(values.size()>=3, "Map is too small: "+values);
            Assert.assertTrue(values.size()<=6, "Map is too big: "+values);
            Assert.assertEquals(values.get("type"), TestApplication.class.getCanonicalName());
            Assert.assertNotNull(values.get("links"), "Map should have contained links");

            // but config etc returns our nicely json serialized
            app.setConfig(TestEntity.CONF_OBJECT, app);
            content = HttpTestUtils.getContent(entityUrl+"/config/"+TestEntity.CONF_OBJECT.getName());
            log.info("CONFIG ENTITY is:\n"+content);
            values = new Gson().fromJson(content, Map.class);
            Assert.assertEquals(values.size(), 2, "Map is wrong size: "+values);
            Assert.assertEquals(values.get("type"), Entity.class.getCanonicalName());
            Assert.assertEquals(values.get("id"), app.getId());

            // and Angry gives the toString
            AngrySelfRefNonSerializableClass angry = new AngrySelfRefNonSerializableClass();
            app.setConfig(TestEntity.CONF_OBJECT, angry);
            content = HttpTestUtils.getContent(entityUrl+"/config/"+TestEntity.CONF_OBJECT.getName());
            log.info("CONFIG ANGRY is:\n"+content);
            String valueS = new Gson().fromJson(content, String.class);
            Assert.assertEquals(valueS, angry.toString());
            
            // as does Server
            app.setConfig(TestEntity.CONF_OBJECT, server);
            content = HttpTestUtils.getContent(entityUrl+"/config/"+TestEntity.CONF_OBJECT.getName());
            log.info("CONFIG SERVER is:\n"+content);
            valueS = new Gson().fromJson(content, String.class);
            Assert.assertEquals(valueS, server.toString());
            
        } finally {
            try {
                server.stop();
            } catch (Exception e) {
                log.warn("failed to stop server: "+e);
            }
            Entities.destroyAll(mgmt);
        }
    }
        
}
