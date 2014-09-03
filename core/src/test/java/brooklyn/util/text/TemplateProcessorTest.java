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
package brooklyn.util.text;

import static org.testng.Assert.assertEquals;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableMap;

public class TemplateProcessorTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testAdditionalArgs() {
        String templateContents = "${mykey}";
        String result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.of("mykey", "myval"));
        assertEquals(result, "myval");
    }
    
    @Test
    public void testEntityConfig() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "myval"));
        String templateContents = "${config['"+TestEntity.CONF_NAME.getName()+"']}";
        String result = TemplateProcessor.processTemplateContents(templateContents, entity, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
    
    @Test
    public void testEntityConfigNumber() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_OBJECT, 123456));
        String templateContents = "${config['"+TestEntity.CONF_OBJECT.getName()+"']}";
        String result = TemplateProcessor.processTemplateContents(templateContents, entity, ImmutableMap.<String,Object>of());
        assertEquals(result, "123,456");
    }
    
    @Test
    public void testEntityConfigNumberUnadorned() {
        // ?c is needed to avoid commas (i always forget this!)
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_OBJECT, 123456));
        String templateContents = "${config['"+TestEntity.CONF_OBJECT.getName()+"']?c}";
        String result = TemplateProcessor.processTemplateContents(templateContents, entity, ImmutableMap.<String,Object>of());
        assertEquals(result, "123456");
    }
    
    @Test
    public void testGetSysProp() {
        System.setProperty("testGetSysProp", "myval");
        
        String templateContents = "${javaSysProps['testGetSysProp']}";
        String result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
    
    @Test
    public void testEntityGetterMethod() {
        String templateContents = "${entity.id}";
        String result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, app.getId());
    }
    
    @Test
    public void testManagementContextConfig() {
        mgmt.getBrooklynProperties().put("globalmykey", "myval");
        String templateContents = "${mgmt.globalmykey}";
        String result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
    
    @Test
    public void testManagementContextConfigWithDot() {
        mgmt.getBrooklynProperties().put("global.mykey", "myval");
        String templateContents = "${mgmt['global.mykey']}";
        String result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
    
    @Test
    public void testManagementContextDefaultValue() {
        String templateContents = "${(missing)!\"defval\"}";
        Object result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, "defval");
    }
    
    @Test
    public void testManagementContextDefaultValueInDotMissingValue() {
        String templateContents = "${(mgmt.missing.more_missing)!\"defval\"}";
        Object result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, "defval");
    }
    
    @Test
    public void testManagementContextErrors() {
        try {
            // NB: dot has special meaning so this should fail
            mgmt.getBrooklynProperties().put("global.mykey", "myval");
            String templateContents = "${mgmt.global.mykey}";
            TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
            Assert.fail("Should not have found value with intermediate dot");
        } catch (Exception e) {
            Assert.assertTrue(e.toString().contains("global"), "Should have mentioned missing key 'global' in error");
        }
    }
    
    @Test
    public void testApplyTemplatedConfigWithAttributeWhenReady() {
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");

        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(app, TestApplication.MY_ATTRIBUTE)));
        
        String templateContents = "${config['"+TestEntity.CONF_NAME.getName()+"']}";
        String result = TemplateProcessor.processTemplateContents(templateContents, entity, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
}
