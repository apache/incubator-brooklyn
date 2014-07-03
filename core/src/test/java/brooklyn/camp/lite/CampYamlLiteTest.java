package brooklyn.camp.lite;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.pdp.PdpYamlTest;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.stream.Streams;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

/** Tests of lightweight CAMP integration. Since the "real" integration is in brooklyn-camp project,
 * but some aspects of CAMP we want to be able to test here. */
public class CampYamlLiteTest {

    private static final Logger log = LoggerFactory.getLogger(CampYamlLiteTest.class);
    
    protected LocalManagementContext mgmt;
    protected CampPlatformWithJustBrooklynMgmt platform;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        mgmt = new LocalManagementContextForTests();
        platform = new CampPlatformWithJustBrooklynMgmt(mgmt);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (mgmt!=null) mgmt.terminate();
    }
    
    /** based on {@link PdpYamlTest} for parsing,
     * then creating a {@link TestAppAssembly} */
    @Test
    public void testYamlServiceMatchAndBrooklynInstantiate() throws Exception {
        MockWebPlatform.populate(platform, TestAppAssemblyInstantiator.class);
        
        Reader input = new InputStreamReader(getClass().getResourceAsStream("test-app-service-blueprint.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);
        log.info("AT is:\n"+at.toString());
        Assert.assertEquals(at.getName(), "sample");
        Assert.assertEquals(at.getPlatformComponentTemplates().links().size(), 1);
        
        // now use brooklyn to instantiate
        Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
        
        TestApplication app = ((TestAppAssembly)assembly).getBrooklynApp();
        Assert.assertEquals( app.getConfig(TestEntity.CONF_NAME), "sample" );
        Map<String, String> map = app.getConfig(TestEntity.CONF_MAP_THING);
        Assert.assertEquals( map.get("desc"), "Tomcat sample JSP and servlet application." );
        
        Assert.assertEquals( app.getChildren().size(), 1 );
        Entity svc = Iterables.getOnlyElement(app.getChildren());
        Assert.assertEquals( svc.getConfig(TestEntity.CONF_NAME), "Hello WAR" );
        map = svc.getConfig(TestEntity.CONF_MAP_THING);
        Assert.assertEquals( map.get("type"), MockWebPlatform.APPSERVER.getType() );
        // desc ensures we got the information from the matcher, as this value is NOT in the yaml
        Assert.assertEquals( map.get("desc"), MockWebPlatform.APPSERVER.getDescription() );
    }

    @Test
    public void testYamlServiceForCatalog() {
        MockWebPlatform.populate(platform, TestAppAssemblyInstantiator.class);
        
        CatalogItem<?, ?> realItem = mgmt.getCatalog().addItem(Streams.readFullyString(getClass().getResourceAsStream("test-app-service-blueprint.yaml")));
        Iterable<CatalogItem<Object, Object>> retrievedItems = mgmt.getCatalog().getCatalogItems(CatalogPredicates.registeredType(Predicates.equalTo("sample")));
        
        Assert.assertEquals(Iterables.size(retrievedItems), 1, "Wrong retrieved items: "+retrievedItems);
        CatalogItem<Object, Object> retrievedItem = Iterables.getOnlyElement(retrievedItems);
        Assert.assertEquals(retrievedItem, realItem);
        
        EntitySpec<?> spec1 = (EntitySpec<?>) mgmt.getCatalog().createSpec(retrievedItem);
        Assert.assertNotNull(spec1);
        Assert.assertEquals(spec1.getConfig().get(TestEntity.CONF_NAME), "sample");
        
        // TODO other assertions, about children
    }
}
