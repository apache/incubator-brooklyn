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
package brooklyn.catalog.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.BrooklynMavenArtifacts;
import brooklyn.util.maven.MavenRetriever;

public class CatalogDtoTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogDtoTest.class);

    private LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test(groups="Integration")
    public void testCatalogLookup() {
        CatalogDto root = buildExampleCatalog();
        checkCatalogHealthy(root);
    }
    
    @Test(groups="Integration")
    public void testCatalogSerializeAndLookup() {
        CatalogDto root = buildExampleCatalog();
        CatalogXmlSerializer serializer = new CatalogXmlSerializer();
        
        String xml = serializer.toString(root);
        log.info("Example catalog serialized as:\n"+xml);
        
        CatalogDto root2 = (CatalogDto) serializer.fromString(xml);
        checkCatalogHealthy(root2);
    }

    protected void checkCatalogHealthy(CatalogDto root) {
        Assert.assertEquals(root.catalogs.size(), 4);
        CatalogDo loader = new CatalogDo(root).load(managementContext, null);
        
        // test app comes from jar, by default
        CatalogItemDo<?,?> worker = loader.getCache().get(TestApplication.class.getCanonicalName());
        Assert.assertNotNull(worker);
        Assert.assertEquals(worker.getName(), "Test App from JAR");
        
        // TODO can test scanned elements, links to other catalogs, etc
    }

    public static CatalogDto buildExampleCatalog() {
        CatalogDo root = new CatalogDo(CatalogDto.newNamedInstance("My Local Catalog", 
                "My favourite local settings, including remote catalogs -- " +
        		"intended partly as a teaching example for what can be expressed, and how",
        		"contents-built-in-test"));
        root.setClasspathScanForEntities(CatalogScanningModes.NONE);
        
        CatalogDo testEntitiesJavaCatalog = new CatalogDo(CatalogDto.newNamedInstance("Test Entities from Java", null, "test-java"));
        testEntitiesJavaCatalog.setClasspathScanForEntities(CatalogScanningModes.NONE);
        testEntitiesJavaCatalog.addToClasspath(MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-core", "jar", "tests")));
        testEntitiesJavaCatalog.addEntry(CatalogItems.newTemplateFromJava(
                TestApplication.class.getCanonicalName(), "Test App from JAR"));
        testEntitiesJavaCatalog.addEntry(CatalogItems.newEntityFromJava(
                TestEntity.class.getCanonicalName(), "Test Entity from JAR"));
        root.addCatalog(testEntitiesJavaCatalog.dto);

        CatalogDo testEntitiesJavaCatalogScanning = new CatalogDo(CatalogDto.newNamedInstance("Test Entities from Java Scanning", null, "test-java-scan"));
        testEntitiesJavaCatalogScanning.addToClasspath(MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-core", "jar", "tests")));
        testEntitiesJavaCatalogScanning.setClasspathScanForEntities(CatalogScanningModes.ANNOTATIONS);
        root.addCatalog(testEntitiesJavaCatalogScanning.dto);
        
        CatalogDo osgiCatalog = new CatalogDo(CatalogDto.newNamedInstance("Test Entities from OSGi",
                "A catalog whose entries define their libraries as a list of OSGi bundles", "test-osgi-defined"));
        osgiCatalog.setClasspathScanForEntities(CatalogScanningModes.NONE);
        CatalogEntityItemDto osgiEntity = CatalogItems.newEntityFromJava(TestEntity.class.getCanonicalName(), "Test Entity from OSGi");
        // NB: this is not actually an OSGi bundle, but it's okay as we don't instantiate the bundles ahead of time (currently)
        osgiEntity.libraries.addBundle(MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-core", "jar", "tests")));
        testEntitiesJavaCatalog.addEntry(osgiEntity);
        root.addCatalog(osgiCatalog.dto);

        root.addCatalog(CatalogDto.newLinkedInstance("classpath://brooklyn-catalog-empty.xml"));
        return root.dto;
    }

}

