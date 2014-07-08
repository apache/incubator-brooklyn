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

import java.io.StringReader;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.StartableApplication;

public class WrapAppTest extends AbstractYamlTest {
    private static final String NO_WRAP_APP_IMPLICIT =
            "name: Empty App\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication";
        
    private static final String NO_WRAP_APP_EXPLICIT =
            "name: Empty App\n" +
            "wrappedApp: false\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication";
        
    private static final String WRAP_APP_IMPLICIT =
            "name: Empty App\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication\n" +
            "   - type: brooklyn.test.entity.TestApplication";
        
    private static final String WRAP_APP_EXPLICIT =
            "name: Empty App\n" +
            "wrappedApp: true\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication";
    
    private static final String WRAP_ENTITY =
            "name: Empty App\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestEntity";
    
    @Test
    public void testNoWrapAppImplicit() throws Exception {
        StartableApplication app = createApp(NO_WRAP_APP_IMPLICIT);
        Assert.assertTrue(app.getChildren().size() == 0);
    }
    
    @Test
    public void testNoWrapAppExplicit() throws Exception {
        StartableApplication app = createApp(NO_WRAP_APP_EXPLICIT);
        Assert.assertTrue(app.getChildren().size() == 0);
    }
    
    @Test
    public void testWrapAppImplicit() throws Exception {
        StartableApplication app = createApp(WRAP_APP_IMPLICIT);
        Assert.assertTrue(app.getChildren().size() == 2);
    }
    
    @Test
    public void testWrapAppExplicit() throws Exception {
        StartableApplication app = createApp(WRAP_APP_EXPLICIT);
        Assert.assertTrue(app.getChildren().size() == 1);
    }
    
    @Test
    public void testWrapEntity() throws Exception {
        StartableApplication app = createApp(WRAP_ENTITY);
        Assert.assertTrue(app.getChildren().size() == 1);
    }
    
    private StartableApplication createApp(String yaml) throws Exception {
        StringReader in = new StringReader(yaml);
        StartableApplication app = (StartableApplication)createAndStartApplication(in);
        in.close();
        return app;
    }
}
