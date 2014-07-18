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

import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DeferredSupplier;

@Test
public class JavaWebAppsMatchingTest {

    private static final Logger log = LoggerFactory.getLogger(JavaWebAppsMatchingTest.class);
    
    private ManagementContext brooklynMgmt;
    private BrooklynCampPlatform platform;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        brooklynMgmt = new LocalManagementContextForTests();
        platform = new BrooklynCampPlatform(
              PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
              brooklynMgmt);
    }
    
    // FIXME all commented-out lines require camp server
    
    @AfterMethod(alwaysRun=true)
    public void teardown() {
        if (brooklynMgmt!=null) Entities.destroyAll(brooklynMgmt);
    }
    
    public void testSimpleYamlParse() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-simple.yaml"));
        DeploymentPlan plan = platform.pdp().parseDeploymentPlan(input);
        log.info("DP is:\n"+plan.toString());
        Assert.assertEquals(plan.getServices().size(), 1);
        Assert.assertEquals(plan.getName(), "sample-single-jboss");
    }
    
    public void testSimpleYamlMatch() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-simple.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);
        
        Assert.assertEquals(at.getName(), "sample-single-jboss");
    }

    public void testExampleFunctionsYamlMatch() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("example-with-function.yaml"));
        
        DeploymentPlan plan = platform.pdp().parseDeploymentPlan(input);
        log.info("DP is:\n"+plan.toString());
        Map<?,?> cfg1 = (Map<?, ?>) plan.getServices().get(0).getCustomAttributes().get("brooklyn.config");
        Map<?,?> cfg = MutableMap.copyOf(cfg1);
        
        Assert.assertEquals(cfg.remove("literalValue1"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("literalValue2"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("literalValue3"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("literalValue4"), "$brooklyn: is a fun place");
        Assert.assertEquals(cfg.remove("$brooklyn:1"), "key to the city");
        Assert.assertTrue(cfg.isEmpty(), ""+cfg);

        Assert.assertEquals(plan.getName(), "example-with-function");
        Assert.assertEquals(plan.getCustomAttributes().get("location"), "localhost");
        
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(plan);
        
        Assert.assertEquals(at.getName(), "example-with-function");
        Assert.assertEquals(at.getCustomAttributes().get("location"), "localhost");
        
        PlatformComponentTemplate pct = at.getPlatformComponentTemplates().links().iterator().next().resolve();
        Object cfg2 = pct.getCustomAttributes().get("brooklyn.config");
        Assert.assertEquals(cfg2, cfg1);
    }

    public void testJavaAndDbWithFunctionYamlMatch() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-and-db-with-function.yaml"));
        assertWebDbWithFunctionValid(input);
    }
    
    public void testJavaAndDbWithFunctionYamlMatch2() throws IOException {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-and-db-with-function-2.yaml"));
        assertWebDbWithFunctionValid(input);
    }
    
    protected void assertWebDbWithFunctionValid(Reader input) { 
        DeploymentPlan plan = platform.pdp().parseDeploymentPlan(input);
        log.info("DP is:\n"+plan.toString());
        
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(plan);
        
        Assert.assertEquals(at.getName(), "java-cluster-db-example");

        Iterator<ResolvableLink<PlatformComponentTemplate>> pcti = at.getPlatformComponentTemplates().links().iterator();
        PlatformComponentTemplate pct1 = pcti.next().resolve(); 

        PlatformComponentTemplate pct2 = pcti.next().resolve(); 

        Map<?,?> config = (Map<?, ?>) pct1.getCustomAttributes().get("brooklyn.config");
        Map<?,?> javaSysProps = (Map<?, ?>) config.get("java.sysprops");
        Object dbUrl = javaSysProps.get("brooklyn.example.db.url");
        Assert.assertTrue(dbUrl instanceof DeferredSupplier<?>, "url is: "+dbUrl);
        
        Assert.assertEquals(pct2.getCustomAttributes().get("planId"), "db");
    }

}
