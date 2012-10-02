package brooklyn.entity.rebind;

import static brooklyn.entity.rebind.RebindTestUtils.rebind;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.util.Collections;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.rebind.RebindEntityTest.MyApplication;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.MutableMap;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CheckpointEntityTest {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointEntityTest.class);

    private ManagementContext origManagementContext;
    private BrooklynMementoPersister persister;
    private MyApplication origApp;
    private MyEntity origE;
    
    @BeforeMethod
    public void setUp() throws Exception {
        File file = File.createTempFile("brooklyn-memento", ".xml");
        LOG.info("Writing brooklyn memento to "+file);
        persister = new BrooklynMementoPersisterToFile(file, getClass().getClassLoader());
//        persister = new BrooklynMementoPersisterInMemory(getClass().getClassLoader());
        
        origManagementContext = new LocalManagementContext();
        origManagementContext.getRebindManager().setPersister(persister);
        origApp = new MyApplication();
        origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        origManagementContext.manage(origApp);
    }
    
    @Test
    public void testAutoCheckpointsOnManageApp() throws Exception {
        MyApplication newApp = (MyApplication) rebind(persister.loadMemento(), getClass().getClassLoader());
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
        origManagementContext.manage(origE2);
        
        MyApplication newApp = (MyApplication) rebind(persister.loadMemento(), getClass().getClassLoader());
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
        origManagementContext.unmanage(origE);
        
        MyApplication newApp = (MyApplication) rebind(persister.loadMemento(), getClass().getClassLoader());
        
        // Assert does not container unmanaged entity
        assertEquals(ImmutableList.copyOf(newApp.getOwnedChildren()), Collections.emptyList());
        assertNull(newApp.getManagementSupport().getManagementContext(false).getEntity(origE.getId()));
    }
    
    @Test
    public void testPersistsOnExplicitCheckpointOfEntity() throws Exception {
        origE.setConfig(MyEntity.MY_CONFIG, "mynewval");
        origE.setAttribute(MyEntity.MY_SENSOR, "mysensorval");
        origE.getManagementSupport().getManagementContext(false).getRebindManager().getChangeListener().onChanged(origE);
        
        // Assert persisted the modified config/attributes
        MyApplication newApp = (MyApplication) rebind(persister.loadMemento(), getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "mynewval");
        assertEquals(newE.getAttribute(MyEntity.MY_SENSOR), "mysensorval");
    }
}
