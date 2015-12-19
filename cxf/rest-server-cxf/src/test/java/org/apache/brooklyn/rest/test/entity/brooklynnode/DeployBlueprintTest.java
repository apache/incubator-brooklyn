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
package org.apache.brooklyn.rest.test.entity.brooklynnode;

import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.EntityManager;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode.DeployBlueprintEffector;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.util.guava.Functionals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;

public class DeployBlueprintTest extends BrooklynRestResourceTest {

    @Override
    protected boolean useLocalScannedCatalog() {
        return true;
    }

    @Override
    protected String getEndpointAddress() {
        return ENDPOINT_ADDRESS_HTTP + "v1";
    }

    private static final Logger log = LoggerFactory.getLogger(DeployBlueprintTest.class);
//
//    Server server;
//
//    @BeforeMethod(alwaysRun=true)
//    public void setUp() throws Exception {
//        server = newServer();
//        useServerForTest(server);
//    }
//
    @Test
    public void testStartsAppViaEffector() throws Exception {
        URI webConsoleUri = URI.create(ENDPOINT_ADDRESS_HTTP); // BrooklynNode will append "/v1" to it

        EntitySpec<BrooklynNode> spec = EntitySpec.create(BrooklynNode.class);
        EntityManager mgr = getManagementContext().getEntityManager(); // getManagementContextFromJettyServerAttributes(server).getEntityManager();
        BrooklynNode node = mgr.createEntity(spec);
        node.sensors().set(BrooklynNode.WEB_CONSOLE_URI, webConsoleUri);
        mgr.manage(node);
        Map<String, String> params = ImmutableMap.of(DeployBlueprintEffector.BLUEPRINT_CAMP_PLAN.getName(), "{ services: [ serviceType: \"java:"+BasicApplication.class.getName()+"\" ] }");
        String id = node.invoke(BrooklynNode.DEPLOY_BLUEPRINT, params).getUnchecked();

        log.info("got: "+id);

        String apps = HttpTestUtils.getContent(getEndpointAddress() + "/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));

        String status = HttpTestUtils.getContent(getEndpointAddress()+"/applications/"+id+"/entities/"+id+"/sensors/service.status");
        log.info("STATUS: "+status);
    }

    private <T> List<T> parseJsonList(String json, List<String> elements, Class<T> clazz) {
        Function<String, List<T>> func = Functionals.chain(
                JsonFunctions.asJson(),
                JsonFunctions.forEach(Functionals.chain(
                        JsonFunctions.walk(elements),
                        JsonFunctions.cast(clazz))));
        return func.apply(json);
    }

}
