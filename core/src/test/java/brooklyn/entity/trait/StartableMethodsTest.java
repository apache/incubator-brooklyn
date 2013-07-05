package brooklyn.entity.trait;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.FailingEntity.RecordingEventListener;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class StartableMethodsTest {

    private SimulatedLocation loc;
    private TestApplication app;
    private TestEntity entity;
    private TestEntity entity2;
    private RecordingEventListener listener;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        listener = new RecordingEventListener();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test
    public void testStopSequentially() {
        entity = app.createAndManageChild(EntitySpecs.spec(FailingEntity.class)
                .configure(FailingEntity.LISTENER, listener));
        entity2 = app.createAndManageChild(EntitySpecs.spec(FailingEntity.class)
                .configure(FailingEntity.LISTENER, listener));
        app.start(ImmutableList.of(loc));
        listener.events.clear();
        
        StartableMethods.stopSequentially(ImmutableList.of(entity, entity2));
        
        assertEquals(listener.events.get(0)[0], entity);
        assertEquals(listener.events.get(1)[0], entity2);
    }
    
    @Test
    public void testStopSequentiallyContinuesOnFailure() {
        try {
            entity = app.createAndManageChild(EntitySpecs.spec(FailingEntity.class)
                    .configure(FailingEntity.FAIL_ON_STOP, true)
                    .configure(FailingEntity.LISTENER, listener));
            entity2 = app.createAndManageChild(EntitySpecs.spec(FailingEntity.class)
                    .configure(FailingEntity.LISTENER, listener));
            app.start(ImmutableList.of(loc));
            listener.events.clear();
            
            try {
                StartableMethods.stopSequentially(ImmutableList.of(entity, entity2));
                fail();
            } catch (Exception e) {
                // success; expected exception to be propagated
            }
            
            assertEquals(listener.events.get(0)[0], entity);
            assertEquals(listener.events.get(1)[0], entity2);
        } finally {
            // get rid of entity that will fail on stop, so that tearDown won't encounter exception
            Entities.unmanage(entity);
        }
    }
}
