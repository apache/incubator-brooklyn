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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.core.management.internal.LocalManagementContext;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.test.entity.TestEntity;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.entity.basic.Entities;
import brooklyn.util.BrooklynMavenArtifacts;
import brooklyn.util.maven.MavenRetriever;

import com.google.common.collect.ImmutableList;

public class CatalogDtoTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogDtoTest.class);

    private LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstanceWithOsgi();
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
        assertEquals(root.catalogs.size(), 4);
        CatalogDo loader = new CatalogDo(managementContext, root).load();
        
        // test app comes from jar, by default
        CatalogItemDo<?,?> worker = loader.getIdCache().get(CatalogUtils.getVersionedId(TestApplication.class.getCanonicalName(), BasicBrooklynCatalog.NO_VERSION));
        assertNotNull(worker);
        assertEquals(worker.getDisplayName(), "Test App from JAR");
        
        // TODO can test scanned elements, links to other catalogs, etc
    }

    public CatalogDto buildExampleCatalog() {
        CatalogDo root = new CatalogDo(
                managementContext,
                CatalogDto.newNamedInstance("My Local Catalog",
                        "My favourite local settings, including remote catalogs -- intended partly as a teaching " +
                        "example for what can be expressed, and how", "contents-built-in-test"));
        root.setClasspathScanForEntities(CatalogScanningModes.NONE);

        String bundleUrl = MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-core", "jar", "tests"));
        CatalogDo testEntitiesJavaCatalog = new CatalogDo(
                managementContext,
                CatalogDto.newNamedInstance("Test Entities from Java", null, "test-java"));
        testEntitiesJavaCatalog.setClasspathScanForEntities(CatalogScanningModes.NONE);
        testEntitiesJavaCatalog.addToClasspath(bundleUrl);
        testEntitiesJavaCatalog.addEntry(CatalogItemBuilder.newTemplate(TestApplication.class.getCanonicalName(), BasicBrooklynCatalog.NO_VERSION)
                .displayName("Test App from JAR")
                .javaType(TestApplication.class.getCanonicalName())
                .build());
        testEntitiesJavaCatalog.addEntry(CatalogItemBuilder.newEntity(TestEntity.class.getCanonicalName(), BasicBrooklynCatalog.NO_VERSION)
                .displayName("Test Entity from JAR")
                .javaType(TestEntity.class.getCanonicalName())
                .build());
        root.addCatalog(testEntitiesJavaCatalog.dto);

        CatalogDo testEntitiesJavaCatalogScanning = new CatalogDo(
                managementContext,
                CatalogDto.newNamedInstance("Test Entities from Java Scanning", null, "test-java-scan"));
        testEntitiesJavaCatalogScanning.addToClasspath(bundleUrl);
        testEntitiesJavaCatalogScanning.setClasspathScanForEntities(CatalogScanningModes.ANNOTATIONS);
        root.addCatalog(testEntitiesJavaCatalogScanning.dto);

        CatalogDo osgiCatalog = new CatalogDo(
                managementContext,
                CatalogDto.newNamedInstance("Test Entities from OSGi",
                        "A catalog whose entries define their libraries as a list of OSGi bundles", "test-osgi-defined"));
        osgiCatalog.setClasspathScanForEntities(CatalogScanningModes.NONE);
        CatalogEntityItemDto osgiEntity = CatalogItemBuilder.newEntity(TestEntity.class.getCanonicalName(), "Test Entity from OSGi")
                // NB: this is not actually an OSGi bundle, but it's okay as we don't instantiate the bundles ahead of time (currently)
                .libraries(ImmutableList.<CatalogBundle>of(new CatalogBundleDto(null, null, bundleUrl)))
                .build();
        testEntitiesJavaCatalog.addEntry(osgiEntity);
        root.addCatalog(osgiCatalog.dto);

        root.addCatalog(CatalogDto.newLinkedInstance("classpath://brooklyn-catalog-empty.xml"));
        return root.dto;
    }
    
    @Test
    public void testVersionedIdSplitter() {
        String id = "simple.id";
        String version = "0.1.2";
        String versionedId = CatalogUtils.getVersionedId(id, version);
        
        Assert.assertNull(CatalogUtils.getIdFromVersionedId(null));
        Assert.assertNull(CatalogUtils.getVersionFromVersionedId(null));
        Assert.assertNull(CatalogUtils.getIdFromVersionedId(id));
        Assert.assertNull(CatalogUtils.getVersionFromVersionedId(version));
        Assert.assertEquals(CatalogUtils.getIdFromVersionedId(versionedId), id);
        Assert.assertEquals(CatalogUtils.getVersionFromVersionedId(versionedId), version);
    }
}
