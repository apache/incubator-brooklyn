package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collection;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.management.ManagementContext;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class RebindDynamicGroupTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private TestApplication origApp;
    private File mementoDir;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = ApplicationBuilder.newManagedApp(EntitySpecs.spec(TestApplication.class), managementContext);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test
    public void testRestoresDynamicGroup() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpecs.spec(MyEntity.class));
        DynamicGroup origG = origApp.createAndManageChild(EntitySpecs.spec(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MyEntity.class)));
        
        TestApplication newApp = rebind();
        final DynamicGroup newG = (DynamicGroup) Iterables.find(newApp.getChildren(), Predicates.instanceOf(DynamicGroup.class));
        final MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));

        // Rebound group should contain same members as last time
        assertGroupMemebers(newG, ImmutableSet.of(newE));

        // And should detect new members that match the filter
        final MyEntity newE2 = newApp.createAndManageChild(EntitySpecs.spec(MyEntity.class));
        Entities.manage(newE2);
        
        TestUtils.assertEventually(new Runnable() {
            public void run() {
                assertGroupMemebers(newG, ImmutableSet.of(newE, newE2));
            }});
    }

    private void assertGroupMemebers(DynamicGroup group, Collection<? extends Entity> expected) {
        assertEquals(Sets.newHashSet(group.getMembers()), ImmutableSet.copyOf(expected));
        assertEquals(group.getMembers().size(), expected.size(), "members="+group.getMembers());
    }
    
    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
