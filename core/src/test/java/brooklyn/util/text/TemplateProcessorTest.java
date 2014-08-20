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
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "myval"));
        String templateContents = "${mykey}";
        String result = TemplateProcessor.processTemplateContents(templateContents, entity, ImmutableMap.of("mykey", "myval"));
        assertEquals(result, "myval");
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
    public void testEntityConfig() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "myval"));
        String templateContents = "${config['"+TestEntity.CONF_NAME.getName()+"']}";
        String result = TemplateProcessor.processTemplateContents(templateContents, entity, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
    
    // TODO Should this be under a sub-map (e.g. globalConfig or some such)?
    @Test
    public void testManagementContextConfig() {
        mgmt.getBrooklynProperties().put("globalmykey", "myval");
        String templateContents = "${globalmykey}";
        String result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
    
    // FIXME Fails because tries to get global, and then call getMyKey() 
    @Test(groups="WIP")
    public void testManagementContextConfigWithDot() {
        mgmt.getBrooklynProperties().put("global.mykey", "myval");
        String templateContents = "${global.mykey}";
        String result = TemplateProcessor.processTemplateContents(templateContents, app, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
    
    // FIXME Fails because does not respect attributeWhenReady; just returns its toString
    @Test(groups="WIP")
    public void testApplyTemplatedConfigWithAttributeWhenReady() {
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");

        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(app, TestApplication.MY_ATTRIBUTE)));
        
        String templateContents = "${config['"+TestEntity.CONF_NAME.getName()+"']}";
        String result = TemplateProcessor.processTemplateContents(templateContents, entity, ImmutableMap.<String,Object>of());
        assertEquals(result, "myval");
    }
}
