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

import java.net.URLEncoder;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;

import brooklyn.catalog.CatalogPredicates;
import brooklyn.catalog.internal.MyCatalogItems.MySillyAppTemplate;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.net.Urls;
import brooklyn.util.text.Strings;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class CatalogScanTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogScanTest.class);

    private BrooklynCatalog defaultCatalog, annotsCatalog, fullCatalog;
    
    private List<LocalManagementContext> managementContexts = Lists.newCopyOnWriteArrayList();

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        for (LocalManagementContext managementContext : managementContexts) {
            Entities.destroyAll(managementContext);
        }
        managementContexts.clear();
    }
    
    private LocalManagementContext newManagementContext(BrooklynProperties props) {
        LocalManagementContext result = new LocalManagementContext(props);
        managementContexts.add(result);
        return result;
    }
    
    private synchronized void loadFullCatalog() {
        if (fullCatalog!=null) return;
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL.getName(), 
                "data:,"+Urls.encode("<catalog><classpath scan=\"types\"/></catalog>"));
        fullCatalog = newManagementContext(props).getCatalog();        
        log.info("ENTITIES loaded for FULL: "+fullCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    private synchronized void loadTheDefaultCatalog(boolean lookOnDiskForDefaultCatalog) {
        if (defaultCatalog!=null) return;
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL.getName(),
            // if default catalog is picked up from the system, we might get random stuff from ~/.brooklyn/ instead of the default;
            // useful as an integration check that we default correctly, but irritating for people to use if they have such a catalog installed
            (lookOnDiskForDefaultCatalog ? "" :
                "data:,"+Urls.encode(new ResourceUtils(this).getResourceAsString("classpath:/brooklyn/default.catalog.bom"))));
        LocalManagementContext managementContext = newManagementContext(props);
        defaultCatalog = managementContext.getCatalog();        
        log.info("ENTITIES loaded for DEFAULT: "+defaultCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    @SuppressWarnings("deprecation")
    private synchronized void loadAnnotationsOnlyCatalog() {
        if (annotsCatalog!=null) return;
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL.getName(),
                "data:,"+URLEncoder.encode("<catalog><classpath scan=\"annotations\"/></catalog>"));
        LocalManagementContext managementContext = newManagementContext(props);
        annotsCatalog = managementContext.getCatalog();
        log.info("ENTITIES loaded with annotation: "+annotsCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    @Test
    public void testLoadAnnotations() {
        loadAnnotationsOnlyCatalog();
        BrooklynCatalog c = annotsCatalog;
        
        Iterable<CatalogItem<Object,Object>> bases = c.getCatalogItems(CatalogPredicates.displayName(Predicates.containsPattern("MyBaseEntity")));
        Assert.assertEquals(Iterables.size(bases), 0, "should have been empty: "+bases);
        
        Iterable<CatalogItem<Object,Object>> asdfjkls = c.getCatalogItems(CatalogPredicates.displayName(Predicates.containsPattern("__asdfjkls__shouldnotbefound")));
        Assert.assertEquals(Iterables.size(asdfjkls), 0);
        
        Iterable<CatalogItem<Object,Object>> silly1 = c.getCatalogItems(CatalogPredicates.displayName(Predicates.equalTo("MySillyAppTemplate")));
        Iterable<CatalogItem<Object,Object>> silly2 = c.getCatalogItems(CatalogPredicates.javaType(Predicates.equalTo(MySillyAppTemplate.class.getName())));
        CatalogItem<Object, Object> silly1El = Iterables.getOnlyElement(silly1);
        Assert.assertEquals(silly1El, Iterables.getOnlyElement(silly2));
        
        CatalogItem<Application,EntitySpec<? extends Application>> s1 = c.getCatalogItem(Application.class, silly1El.getSymbolicName(), silly1El.getVersion());
        Assert.assertEquals(s1, silly1El);
        
        Assert.assertEquals(s1.getDescription(), "Some silly app test");
        
        Class<? extends Application> app = c.loadClass(s1);
        Assert.assertEquals(MySillyAppTemplate.class, app);
        
        String xml = ((BasicBrooklynCatalog)c).toXmlString();
        log.info("Catalog is:\n"+xml);
        Assert.assertTrue(xml.indexOf("Some silly app test") >= 0);
    }

    @Test
    public void testAnnotationLoadsSomeApps() {
        loadAnnotationsOnlyCatalog();
        Iterable<CatalogItem<Object,Object>> silly1 = annotsCatalog.getCatalogItems(CatalogPredicates.displayName(Predicates.equalTo("MySillyAppTemplate")));
        Assert.assertEquals(Iterables.getOnlyElement(silly1).getDescription(), "Some silly app test");
    }
    
    @Test
    public void testAnnotationLoadsSomeAppBuilders() {
        loadAnnotationsOnlyCatalog();
        Iterable<CatalogItem<Object,Object>> silly1 = annotsCatalog.getCatalogItems(CatalogPredicates.displayName(Predicates.equalTo("MySillyAppBuilderTemplate")));
        Assert.assertEquals(Iterables.getOnlyElement(silly1).getDescription(), "Some silly app builder test");
    }
    
    @Test
    public void testMoreTypesThanAnnotations() {
        loadAnnotationsOnlyCatalog();
        loadFullCatalog();
        
        int numFromAnnots = Iterables.size(annotsCatalog.getCatalogItems(Predicates.alwaysTrue()));
        int numFromTypes = Iterables.size(fullCatalog.getCatalogItems(Predicates.alwaysTrue()));
        
        Assert.assertTrue(numFromAnnots < numFromTypes, "full="+numFromTypes+" annots="+numFromAnnots);
    }
    
    @Test
    public void testMoreTypesThanAnnotationsForApps() {
        loadAnnotationsOnlyCatalog();
        loadFullCatalog();
        
        int numFromAnnots = Iterables.size(annotsCatalog.getCatalogItems(CatalogPredicates.IS_TEMPLATE));
        int numFromTypes = Iterables.size(fullCatalog.getCatalogItems(CatalogPredicates.IS_TEMPLATE));
        
        Assert.assertTrue(numFromAnnots < numFromTypes, "full="+numFromTypes+" annots="+numFromAnnots);
    }
    
    @Test
    public void testAnnotationIsDefault() {
        doTestAnnotationIsDefault(false);
    }
    
    // see comment in load method; likely fails if a custom catalog is installed in ~/.brooklyn/
    @Test(groups="Integration", enabled=false)
    public void testAnnotationIsDefaultOnDisk() {
        doTestAnnotationIsDefault(true);
    }

    private void doTestAnnotationIsDefault(boolean lookOnDiskForDefaultCatalog) {
        loadTheDefaultCatalog(false);
        int numInDefault = Iterables.size(defaultCatalog.getCatalogItems(Predicates.alwaysTrue()));
        
        loadAnnotationsOnlyCatalog();
        int numFromAnnots = Iterables.size(annotsCatalog.getCatalogItems(Predicates.alwaysTrue()));
        
        Assert.assertEquals(numInDefault, numFromAnnots);
        Assert.assertTrue(numInDefault>0, "Expected more than 0 entries");
    }

    // a simple test asserting no errors when listing the real catalog, and listing them for reference
    // also useful to test variants in a stored catalog to assert they all load
    // TODO integration tests which build up catalogs assuming other things are installed
    @Test
    public void testListCurrentCatalogItems() {
        LocalManagementContext mgmt = newManagementContext(BrooklynProperties.Factory.newDefault());
        log.info("ITEMS\n"+Strings.join(mgmt.getCatalog().getCatalogItems(), "\n"));
    }

}
