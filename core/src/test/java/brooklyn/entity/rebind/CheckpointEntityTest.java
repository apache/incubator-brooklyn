package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.util.Collections;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class CheckpointEntityTest {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointEntityTest.class);

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext origManagementContext;
    private File mementoDir;
    private TestApplication origApp;
    private TestApplication newApp;
    private MyEntity origE;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), origManagementContext);
        origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class).configure("myconfig", "myval"));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (origManagementContext != null) Entities.destroyAll(origManagementContext);
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @Test
    public void testAutoCheckpointsOnManageApp() throws Exception {
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        // Assert has expected entities and config
        assertEquals(newApp.getId(), origApp.getId());
        assertEquals(ImmutableList.copyOf(newApp.getChildren()), ImmutableList.of(newE));
        assertEquals(newE.getId(), origE.getId());
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testAutoCheckpointsOnManageDynamicEntity() throws Exception {
        final MyEntity origE2 = origApp.createAndManageChild(EntitySpec.create(MyEntity.class).configure("myconfig", "myval2"));
        Entities.manage(origE2);
        
        newApp = rebind();
        MyEntity newE2 = (MyEntity) Iterables.find(newApp.getChildren(), new Predicate<Entity>() {
                @Override public boolean apply(@Nullable Entity input) {
                    return origE2.getId().equals(input.getId());
                }});
        
        // Assert has expected entities and config
        assertEquals(newApp.getChildren().size(), 2); // expect equivalent of origE and origE2
        assertEquals(newE2.getId(), origE2.getId());
        assertEquals(newE2.getConfig(MyEntity.MY_CONFIG), "myval2");
    }
    
    @Test
    public void testAutoCheckpointsOnUnmanageEntity() throws Exception {
        Entities.unmanage(origE);
        
        newApp = rebind();
        
        // Assert does not container unmanaged entity
        assertEquals(ImmutableList.copyOf(newApp.getChildren()), Collections.emptyList());
        assertNull(((EntityInternal)newApp).getManagementContext().getEntityManager().getEntity(origE.getId()));
    }
    
    @Test
    public void testPersistsOnExplicitCheckpointOfEntity() throws Exception {
        origE.setConfig(MyEntity.MY_CONFIG, "mynewval");
        origE.setAttribute(MyEntity.MY_SENSOR, "mysensorval");
        
        // Assert persisted the modified config/attributes
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "mynewval");
        assertEquals(newE.getAttribute(MyEntity.MY_SENSOR), "mysensorval");
    }
    
    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
