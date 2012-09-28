package brooklyn.entity.rebind;

import static brooklyn.entity.rebind.RebindTestUtils.serializeRebindAndManage;
import static org.testng.Assert.assertEquals;

import java.util.Collection;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class RebindDynamicGroupTest {

    private TestApplication origApp;

    @BeforeMethod
    public void setUp() throws Exception {
        origApp = new TestApplication();
    }
    
    @Test
    public void testRestoresDynamicGroup() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        DynamicGroup origG = new DynamicGroup(origApp, Predicates.instanceOf(MyEntity.class));
        origApp.getManagementContext().manage(origApp);
        
        TestApplication newApp = (TestApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        final DynamicGroup newG = (DynamicGroup) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(DynamicGroup.class));
        final MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));

        // Rebound group should contain same members as last time
        assertGroupMemebers(newG, ImmutableSet.of(newE));

        // And should detect new members that match the filter
        final MyEntity newE2 = new MyEntity(newApp);
        newApp.getManagementContext().manage(newE2);
        
        TestUtils.assertEventually(new Runnable() {
            public void run() {
                assertGroupMemebers(newG, ImmutableSet.of(newE, newE2));
            }});
    }

    private void assertGroupMemebers(DynamicGroup group, Collection<? extends Entity> expected) {
        assertEquals(Sets.newHashSet(group.getMembers()), ImmutableSet.copyOf(expected));
        assertEquals(group.getMembers().size(), expected.size(), "members="+group.getMembers());
    }
}
