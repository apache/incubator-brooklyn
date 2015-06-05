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
package brooklyn.entity.rebind.transformer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.catalog.internal.CatalogBundleDto;
import brooklyn.catalog.internal.CatalogEntityItemDto;
import brooklyn.catalog.internal.CatalogItemBuilder;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.TransformerLoader;
import brooklyn.entity.rebind.dto.MementoManifestImpl;
import brooklyn.entity.rebind.transformer.RawDataTransformer;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.management.osgi.OsgiTestResources;
import brooklyn.mementos.BrooklynMementoManifest.MementoManifest;
import brooklyn.test.TestResourceUnavailableException;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.osgi.Osgis;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class TransformerLoaderTest {
    private LocalManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    private void setUp() throws BundleException {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        mgmt = LocalManagementContextForTests.builder(true).disableOsgi(false).build();
        Framework framework = mgmt.getOsgiManager().get().getFramework();
        Osgis.install(framework, OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH);
    }

    @AfterMethod(alwaysRun=true)
    private void tearDown() {
        Entities.destroyAll(mgmt);
    }

    @Test
    public void testLoadsGlobalTransformers() throws Exception {
        Collection<RawDataTransformer> transformers = new TransformerLoader(mgmt).findGlobalTransformers();
        assertEquals(transformers.size(), 2, "One transformer in core, one in osgi bundle");
        Set<String> transformerNames = new HashSet<String>();
        for (RawDataTransformer t : transformers) {
            transformerNames.add(t.getClass().getSimpleName());
            assertEquals(t.transform("test"), t.getClass().getSimpleName());
        }

        assertTrue(transformerNames.contains("GlobalTestTransformer"), "Missing TestGlobalTransformer");
        assertTrue(transformerNames.contains("TestGlobalOsgiTransformer"), "Missing TestGlobalOsgiTransformer");
    }
    
    @DataProvider(name="transformers")
    public Object[][] trasnfromers() {
        return new Object[][] {
             {TransformEntityAnnotated.class.getName(),
                     TransformEntityAnnotationTransformer.class.getName(),
                     null},
             {TransformEntityNamed.class.getName(),
                     TransformEntityNamedTransformer.class.getName(),
                     null},
             {"brooklyn.osgi.tests.transforms.TransformOsgiEntityAnnotated",
                     "brooklyn.osgi.tests.transforms.TransformOsgiEntityAnnotationTransformer",
                     OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH},
             {"brooklyn.osgi.tests.transforms.TransformOsgiEntityNamed",
                    "brooklyn.osgi.tests.transforms.TransformOsgiEntityNamedTransformer",
                    OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH}
        };
    }

    @Test(dataProvider="transformers")
    public void testLoadsBlueprintTransformers(String entityType, String transformerType, String jarPath) throws Exception {
        String catalogItemId = null;
        if (jarPath != null) {
            TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), jarPath);
            CatalogEntityItemDto item = CatalogItemBuilder.newEntity("test", "1.0")
                .libraries(ImmutableList.of(new CatalogBundleDto(null, null, "classpath:" + jarPath)))
                .build();
            mgmt.getCatalog().addItem(item);
            catalogItemId = CatalogUtils.getVersionedId(item.getSymbolicName(), item.getVersion());
        }
        MementoManifest manifest = new MementoManifestImpl("<id>", entityType, null, catalogItemId);
        Collection<RawDataTransformer> transformers = new TransformerLoader(mgmt).findBlueprintTransformers(manifest);
        RawDataTransformer transformer = Iterables.getOnlyElement(transformers);
        assertEquals(transformer.getClass().getName(), transformerType);
        assertEquals(transformer.transform(""), transformer.getClass().getName());
   }
}
