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
package brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.brooklynnode.BrooklynNodeImpl.DeployBlueprintEffectorBody;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.rest.BrooklynRestApiLauncherTestFixture;
import brooklyn.test.HttpTestUtils;
import brooklyn.util.http.HttpTool;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class DeployBlueprintTest extends BrooklynRestApiLauncherTestFixture {

    private static final Logger log = LoggerFactory.getLogger(DeployBlueprintTest.class);
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        useServerForTest(newServer());
    }

    @Test
    public void testStartsAppViaEffector() throws Exception {
        URI webConsoleUri = URI.create(getBaseUri());
        
        String id = DeployBlueprintEffectorBody.submitPlan(HttpTool.httpClientBuilder().build(), webConsoleUri, 
            "{ services: [ serviceType: \"java:"+BasicApplication.class.getName()+"\" ] }");

        log.info("got: "+id);
        
        String apps = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));
        
        String status = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications/"+id+"/entities/"+id+"/sensors/service.status");
        log.info("STATUS: "+status);
    }
    
    private <T> List<T> parseJsonList(String json, List<String> elements, Class<T> clazz) {
        Function<String, List<T>> func = HttpValueFunctions.chain(
                JsonFunctions.asJson(),
                JsonFunctions.forEach(HttpValueFunctions.chain(
                        JsonFunctions.walk(elements),
                        JsonFunctions.cast(clazz))));
        return func.apply(json);
    }

}
