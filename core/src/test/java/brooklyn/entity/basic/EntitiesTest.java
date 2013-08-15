package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class EntitiesTest {

    private static final int TIMEOUT_MS = 10*1000;
    
    private SimulatedLocation loc;
    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testAttributeSupplier() throws Exception {
        entity.setAttribute(TestEntity.NAME, "myname");
        assertEquals(Entities.attributeSupplier(entity, TestEntity.NAME).get(), "myname");
    }
    
    @Test
    public void testAttributeSupplierUsingTuple() throws Exception {
        entity.setAttribute(TestEntity.NAME, "myname");
        assertEquals(Entities.attributeSupplier(new EntityAndAttribute<String>(entity, TestEntity.NAME)).get(), "myname");
    }
    
    @Test(groups="Integration") // takes 1 second
    public void testAttributeSupplierWhenReady() throws Exception {
        final AtomicReference<String> result = new AtomicReference<String>();
        
        final Thread t = new Thread(new Runnable() {
            @Override public void run() {
                result.set(Entities.attributeSupplierWhenReady(entity, TestEntity.NAME).get());
                
            }});
        try {
            t.start();
            
            // should block, waiting for value
            Asserts.succeedsContinually(new Runnable() {
                @Override public void run() {
                    assertTrue(t.isAlive());
                }
            });
            
            entity.setAttribute(TestEntity.NAME, "myname");
            t.join(TIMEOUT_MS);
            assertFalse(t.isAlive());
            assertEquals(result.get(), "myname");
        } finally {
            t.interrupt();
        }
        
        // And now that it's set, the attribute-when-ready should return immediately
        assertEquals(Entities.attributeSupplierWhenReady(entity, TestEntity.NAME).get(), "myname");
    }
}
