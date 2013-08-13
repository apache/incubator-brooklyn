package brooklyn.catalog.internal;

import java.net.URLEncoder;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.catalog.internal.MyCatalogItems.MySillyAppTemplate;
import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;

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
        props.put(LocalManagementContext.BROOKLYN_CATALOG_URL.getName(), 
                "data:,"+URLEncoder.encode("<catalog><classpath scan=\"types\"/></catalog>"));
        fullCatalog = newManagementContext(props).getCatalog();        
        log.info("ENTITIES loaded for FULL: "+fullCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    private synchronized void loadTheDefaultCatalog() {
        if (defaultCatalog!=null) return;
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(LocalManagementContext.BROOKLYN_CATALOG_URL.getName(), "");
        LocalManagementContext managementContext = newManagementContext(props);
        defaultCatalog = managementContext.getCatalog();        
        log.info("ENTITIES loaded for DEFAULT: "+defaultCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    @SuppressWarnings("deprecation")
    private synchronized void loadAnnotationsOnlyCatalog() {
        if (annotsCatalog!=null) return;
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(LocalManagementContext.BROOKLYN_CATALOG_URL.getName(),
                "data:,"+URLEncoder.encode("<catalog><classpath scan=\"annotations\"/></catalog>"));
        LocalManagementContext managementContext = newManagementContext(props);
        annotsCatalog = managementContext.getCatalog();
        log.info("ENTITIES loaded with annotation: "+annotsCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    @Test
    public void testLoadAnnotations() {
        loadAnnotationsOnlyCatalog();
        BrooklynCatalog c = annotsCatalog;
        
        Iterable<CatalogItem<Object>> bases = c.getCatalogItems(CatalogPredicates.name(Predicates.containsPattern("MyBaseEntity")));
        Assert.assertEquals(Iterables.size(bases), 0, "should have been empty: "+bases);
        
        Iterable<CatalogItem<Object>> asdfjkls = c.getCatalogItems(CatalogPredicates.name(Predicates.containsPattern("__asdfjkls__shouldnotbefound")));
        Assert.assertEquals(Iterables.size(asdfjkls), 0);
        
        Iterable<CatalogItem<Object>> silly1 = c.getCatalogItems(CatalogPredicates.name(Predicates.equalTo("MySillyAppTemplate")));
        Iterable<CatalogItem<Object>> silly2 = c.getCatalogItems(CatalogPredicates.javaType(Predicates.equalTo(MySillyAppTemplate.class.getName())));
        Assert.assertEquals(Iterables.getOnlyElement(silly1), Iterables.getOnlyElement(silly2));
        
        CatalogItem<Application> s1 = c.getCatalogItem(Application.class, silly1.iterator().next().getId());
        Assert.assertEquals(s1, Iterables.getOnlyElement(silly1));
        
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
        Iterable<CatalogItem<Object>> silly1 = annotsCatalog.getCatalogItems(CatalogPredicates.name(Predicates.equalTo("MySillyAppTemplate")));
        Assert.assertEquals(Iterables.getOnlyElement(silly1).getDescription(), "Some silly app test");
    }
    
    @Test
    public void testAnnotationLoadsSomeAppBuilders() {
        loadAnnotationsOnlyCatalog();
        Iterable<CatalogItem<Object>> silly1 = annotsCatalog.getCatalogItems(CatalogPredicates.name(Predicates.equalTo("MySillyAppBuilderTemplate")));
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
        loadAnnotationsOnlyCatalog();
        loadTheDefaultCatalog();
        
        int numFromAnnots = Iterables.size(annotsCatalog.getCatalogItems(Predicates.alwaysTrue()));
        int numInDefault = Iterables.size(defaultCatalog.getCatalogItems(Predicates.alwaysTrue()));
        
        Assert.assertEquals(numFromAnnots, numInDefault);
    }
    
}
