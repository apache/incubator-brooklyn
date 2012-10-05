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
import brooklyn.entity.rebind.RebindEntityTest.MyApplication;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.management.ManagementContext;
import brooklyn.util.MutableMap;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class CheckpointEntityTest {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointEntityTest.class);

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private File mementoDir;
    private MyApplication origApp;
    private MyEntity origE;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        origApp = new MyApplication();
        origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        managementContext.manage(origApp);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @Test
    public void testAutoCheckpointsOnManageApp() throws Exception {
        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        // Assert has expected entities and config
        assertEquals(newApp.getId(), origApp.getId());
        assertEquals(ImmutableList.copyOf(newApp.getOwnedChildren()), ImmutableList.of(newE));
        assertEquals(newE.getId(), origE.getId());
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testAutoCheckpointsOnManageDynamicEntity() throws Exception {
        final MyEntity origE2 = new MyEntity(MutableMap.of("myconfig", "myval2"), origApp);
        managementContext.manage(origE2);
        
        MyApplication newApp = rebind();
        MyEntity newE2 = (MyEntity) Iterables.find(newApp.getOwnedChildren(), new Predicate<Entity>() {
                @Override public boolean apply(@Nullable Entity input) {
                    return origE2.getId().equals(input.getId());
                }});
        
        // Assert has expected entities and config
        assertEquals(newApp.getOwnedChildren().size(), 2); // expect equivalent of origE and origE2
        assertEquals(newE2.getId(), origE2.getId());
        assertEquals(newE2.getConfig(MyEntity.MY_CONFIG), "myval2");
    }
    
    @Test
    public void testAutoCheckpointsOnUnmanageEntity() throws Exception {
        managementContext.unmanage(origE);
        
        MyApplication newApp = rebind();
        
        // Assert does not container unmanaged entity
        assertEquals(ImmutableList.copyOf(newApp.getOwnedChildren()), Collections.emptyList());
        assertNull(newApp.getManagementSupport().getManagementContext(false).getEntity(origE.getId()));
    }
    
    @Test
    public void testPersistsOnExplicitCheckpointOfEntity() throws Exception {
        origE.setConfig(MyEntity.MY_CONFIG, "mynewval");
        origE.setAttribute(MyEntity.MY_SENSOR, "mysensorval");
        
        // Assert persisted the modified config/attributes
        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "mynewval");
        assertEquals(newE.getAttribute(MyEntity.MY_SENSOR), "mysensorval");
    }
    
    private MyApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (MyApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
