package brooklyn.entity.rebind;

import static brooklyn.entity.rebind.RebindTestUtils.rebind;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindEntityTest.MyApplication;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CheckpointEntityTest {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointEntityTest.class);

    private MyApplication origApp;
    private BrooklynMementoPersister persister;

    @BeforeMethod
    public void setUp() throws Exception {
        File file = File.createTempFile("brooklyn-memento", ".xml");
        LOG.info("Writing brooklyn memento to "+file);
        persister = new BrooklynMementoPersisterToFile(file, getClass().getClassLoader());
//        persister = new BrooklynMementoPersisterInMemory(getClass().getClassLoader());
        
        origApp = new MyApplication();
        origApp.getManagementContext().getRebindManager().setPersister(persister);
    }
    
    @Test
    public void testAutoCheckpointsOnManageApp() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        origApp.getManagementContext().manage(origApp);
        
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
        origApp.getManagementContext().manage(origApp);

        MyEntity origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        origApp.getManagementContext().manage(origE);
        
        MyApplication newApp = (MyApplication) rebind(persister.loadMemento(), getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        // Assert has expected entities and config
        assertEquals(ImmutableList.copyOf(newApp.getOwnedChildren()), ImmutableList.of(newE));
        assertEquals(newE.getId(), origE.getId());
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testAutoCheckpointsOnUnmanageEntity() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        origApp.getManagementContext().manage(origApp);
        origApp.getManagementContext().unmanage(origE);
        
        MyApplication newApp = (MyApplication) rebind(persister.loadMemento(), getClass().getClassLoader());
        
        // Assert does not container unmanaged entity
        assertEquals(ImmutableList.copyOf(newApp.getOwnedChildren()), Collections.emptyList());
        assertNull(newApp.getManagementContext().getEntity(origE.getId()));
    }
    
    @Test
    public void testPersistsOnExplicitCheckpointOfEntity() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        origApp.getManagementContext().manage(origApp);

        origE.setConfig(MyEntity.MY_CONFIG, "mynewval");
        origE.setAttribute(MyEntity.MY_SENSOR, "mysensorval");
        origE.getManagementContext().getRebindManager().getChangeListener().onChanged(origE);
        
        // Assert persisted the modified config/attributes
        MyApplication newApp = (MyApplication) rebind(persister.loadMemento(), getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "mynewval");
        assertEquals(newE.getAttribute(MyEntity.MY_SENSOR), "mysensorval");
    }
}
