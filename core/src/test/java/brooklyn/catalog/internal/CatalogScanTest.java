package brooklyn.catalog.internal;

import java.net.URLEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.catalog.internal.MyCatalogItems.MySillyAppTemplate;
import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class CatalogScanTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogScanTest.class);
    private BrooklynCatalog defaultCatalog, annotsCatalog;
    
    private synchronized void loadDefaultCatalog() {
        if (defaultCatalog!=null) return;
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(LocalManagementContext.BROOKLYN_CATALOG_URL.getName(), "");
        defaultCatalog = new LocalManagementContext(props).getCatalog();        
        log.info("ENTITIES loaded: "+defaultCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    @SuppressWarnings("deprecation")
    private synchronized void loadAnnotationsOnlyCatalog() {
        if (annotsCatalog!=null) return;
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(LocalManagementContext.BROOKLYN_CATALOG_URL.getName(), 
                "data:,"+URLEncoder.encode("<catalog><classpath scan=\"annotations\"/></catalog>"));
        annotsCatalog = new LocalManagementContext(props).getCatalog();        
        log.info("ENTITIES loaded with annotation: "+annotsCatalog.getCatalogItems(Predicates.alwaysTrue()));
    }
    
    @Test
    public void testDefaultScansAll() {
        loadDefaultCatalog();
        
        Iterable<CatalogItem<Object>> bases = defaultCatalog.getCatalogItems(CatalogPredicates.name(Predicates.containsPattern("MyBaseEntity")));
        Assert.assertNotEquals(Iterables.size(bases), 0);
        
        Iterable<CatalogItem<Object>> asdfjkls = defaultCatalog.getCatalogItems(CatalogPredicates.name(Predicates.containsPattern("__asdfjkls__shouldnotbefound")));
        Assert.assertEquals(Iterables.size(asdfjkls), 0);
        
        Iterable<CatalogItem<Object>> silly1 = defaultCatalog.getCatalogItems(CatalogPredicates.name(Predicates.equalTo("MySillyAppTemplate")));
        Iterable<CatalogItem<Object>> silly2 = defaultCatalog.getCatalogItems(CatalogPredicates.javaType(Predicates.equalTo(MySillyAppTemplate.class.getName())));
        Assert.assertEquals(Iterables.getOnlyElement(silly1), Iterables.getOnlyElement(silly2));
        
        CatalogItem<Application> s1 = defaultCatalog.getCatalogItem(Application.class, silly1.iterator().next().getId());
        Assert.assertEquals(s1, Iterables.getOnlyElement(silly1));
        
        Assert.assertEquals(s1.getDescription(), "Some silly test");
        
        Class<? extends Application> app = defaultCatalog.loadClass(s1);
        Assert.assertEquals(MySillyAppTemplate.class, app);
        
        String xml = ((BasicBrooklynCatalog)defaultCatalog).toXmlString();
        log.info("Catalog is:\n"+xml);
        Assert.assertTrue(xml.indexOf("Some silly test") >= 0);
    }

    @Test
    public void testAnnotationLoadsSome() {
        loadAnnotationsOnlyCatalog();
        Iterable<CatalogItem<Object>> silly1 = annotsCatalog.getCatalogItems(CatalogPredicates.name(Predicates.equalTo("MySillyAppTemplate")));
        Assert.assertEquals(Iterables.getOnlyElement(silly1).getDescription(), "Some silly test");
    }
    
    @Test
    public void testAnnotationFindsFewer() {
        loadAnnotationsOnlyCatalog();
        loadDefaultCatalog();
        
        int numFromAnnots = Iterables.size(annotsCatalog.getCatalogItems(Predicates.alwaysTrue()));
        int numFromTypes = Iterables.size(defaultCatalog.getCatalogItems(Predicates.alwaysTrue()));
        
        Assert.assertTrue(numFromAnnots < numFromTypes);
    }
    
}
