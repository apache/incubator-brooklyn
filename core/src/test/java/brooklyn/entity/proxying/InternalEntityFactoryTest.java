package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;

public class InternalEntityFactoryTest {

    private ManagementContext managementContext;
    private InternalEntityFactory factory;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        factory = new InternalEntityFactory(managementContext, managementContext.getEntityManager().getEntityTypeRegistry());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext instanceof AbstractManagementContext) ((AbstractManagementContext)managementContext).terminate();
    }
    
    @Test
    public void testCreatesEntity() throws Exception {
        EntitySpec<BasicApplication> spec = BasicEntitySpec.newInstance(BasicApplication.class);
        BasicApplicationImpl app = (BasicApplicationImpl) factory.createEntity(spec);
        
        Entity proxy = app.getProxy();
        assertTrue(proxy instanceof Application, "proxy="+app);
        assertFalse(proxy instanceof BasicApplicationImpl, "proxy="+app);
        
        assertEquals(proxy.getParent(), null);
        assertSame(proxy.getApplication(), proxy);
    }
    
    @Test
    public void testCreatesProxy() throws Exception {
        BasicApplication app = new BasicApplicationImpl();
        Application proxy = factory.createEntityProxy(Application.class, app);
        
        assertFalse(proxy instanceof BasicApplication, "proxy="+app);
        assertTrue(proxy instanceof EntityProxy, "proxy="+app);
    }
    
    @Test
    public void testSetsEntityIsLegacyConstruction() throws Exception {
        TestEntity legacy = new TestEntityImpl();
        assertTrue(legacy.isLegacyConstruction());
        
        TestEntity entity = factory.createEntity(BasicEntitySpec.newInstance(TestEntity.class));
        assertFalse(entity.isLegacyConstruction());
    }
}
