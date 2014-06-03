package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.entity.rebind.RebindEntityTest.MyEntityImpl;
import brooklyn.entity.rebind.RebindManager.RebindFailureMode;
import brooklyn.event.AttributeSensor;
import brooklyn.management.EntityManager;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.os.Os;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindFailuresTest extends RebindTestFixtureWithApp {

    @Test
    public void testFailureGeneratingMementoStillPersistsOtherEntities() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_GENERATE_MEMENTO, true));
        
        newApp = rebind(false);
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), EntityPredicates.idEqualTo(origE.getId()));
        Optional<Entity> newFailingE = Iterables.tryFind(newApp.getChildren(), EntityPredicates.idEqualTo(origFailingE.getId()));
        
        // Expect origFailingE to never have been persisted, but origE to have worked
        assertNotNull(newE);
        assertFalse(newFailingE.isPresent(), "newFailedE="+newFailingE);
    }

    @Test(invocationCount=10, groups="Integration")
    public void testFailureGeneratingMementoStillPersistsOtherEntitiesRepeatedly() throws Exception {
        testFailureGeneratingMementoStillPersistsOtherEntities();
    }

    @Test
    public void testFailureRebindingEntityWhenFailAtEnd() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_AT_END;
        
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_REBIND, true));
        
        newManagementContext = new LocalManagementContext();
        EntityManager newEntityManager = newManagementContext.getEntityManager();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        try {
            newApp = rebind(newManagementContext, exceptionHandler);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Problems rebinding")) throw e; // expected
        }

        // exception handler should have been told about failure
        assertEquals(toEntityIds(exceptionHandler.rebindEntityFailures.keySet()), ImmutableSet.of(origFailingE.getId()));

        // Expect that on failure will have continued with rebind, and then report all problems
        assertEquals(toEntityIds(newEntityManager.getEntities()), ImmutableSet.of(origApp.getId(), origFailingE.getId()));
    }
    
    @Test
    public void testFailureRebindingEntityWhenFailFast() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_FAST;
        
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_REBIND, true));
        
        newManagementContext = new LocalManagementContext();
        EntityManager newEntityManager = newManagementContext.getEntityManager();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        try {
            newApp = rebind(newManagementContext, exceptionHandler);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Problems rebinding")) throw e; // expected
        }

        // exception handler should have been told about failure
        assertEquals(toEntityIds(exceptionHandler.rebindEntityFailures.keySet()), ImmutableSet.of(origFailingE.getId()));
        
        // entities will not have been managed
        assertEquals(toEntityIds(newEntityManager.getEntities()), ImmutableSet.of());
    }
    
    @Test
    public void testFailureRebindingEntityWhenContinue() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_REBIND, true));
        
        newManagementContext = new LocalManagementContext();
        EntityManager newEntityManager = newManagementContext.getEntityManager();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        newApp = rebind(newManagementContext, exceptionHandler);

        // exception handler should have been told about failure
        assertEquals(toEntityIds(exceptionHandler.rebindEntityFailures.keySet()), ImmutableSet.of(origFailingE.getId()));
        
        // TODO How should brooklyn indicate that this entity's rebind failed? What can we assert?
        assertEquals(toEntityIds(newEntityManager.getEntities()), ImmutableSet.of(origApp.getId(), origFailingE.getId()));
    }
    
    @Test
    public void testFailureRebindingBecauseDirectoryCorrupt() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_AT_END;
        
        origManagementContext.getRebindManager().stop();
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
        File entitiesDir = Os.mkdirs(new File(mementoDir, "entities"));
        Files.write("invalid text", new File(entitiesDir, "mycorruptfile"), Charsets.UTF_8);
        
        newManagementContext = new LocalManagementContext();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        try {
            newApp = rebind(newManagementContext, exceptionHandler);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Problems rebinding")) throw e; // expected
        }
        
        // exception handler should have been told about failure
        // two exceptions: one for loadMementoManifest; one for loadMemento
        assertEquals(exceptionHandler.loadMementoFailures.size(), 2, "exceptions="+exceptionHandler.loadMementoFailures);
    }

    private Set<String> toEntityIds(Iterable<? extends Entity> entities) {
        return ImmutableSet.copyOf(Iterables.transform(entities, EntityFunctions.id()));
    }
    
    public static class MyEntityFailingImpl extends MyEntityImpl implements MyEntity {
        @SetFromFlag("failOnGenerateMemento")
        public static final ConfigKey<Boolean> FAIL_ON_GENERATE_MEMENTO = ConfigKeys.newBooleanConfigKey("failOnGenerateMemento", "Whether to throw exception when generating memento", false);
        
        @SetFromFlag("failOnRebind")
        public static final ConfigKey<Boolean> FAIL_ON_REBIND = ConfigKeys.newBooleanConfigKey("failOnRebind", "Whether to throw exception when rebinding", false);
        
        @SuppressWarnings("rawtypes")
        @Override
        public Map<AttributeSensor, Object> getAllAttributes() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_GENERATE_MEMENTO))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause memento-generation to fail");
            } else {
                return super.getAllAttributes();
            }
        }
        
        @Override
        public void rebind() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_REBIND))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause rebind to fail");
            }
        }
    }
}
