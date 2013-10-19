package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.Iterables;

public class EntitySpecTest {

    private static final int TIMEOUT_MS = 10*1000;
    
    private SimulatedLocation loc;
    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testSetsConfig() throws Exception {
        // TODO Test other permutations
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "myname"));
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "myname");
    }

    @Test
    public void testAddsPolicySpec() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(PolicySpec.create(MyPolicy.class)
                        .displayName("mypolicyname")
                        .configure(MyPolicy.CONF1, "myconf1val")
                        .configure("myfield", "myfieldval")));
        
        Policy policy = Iterables.getOnlyElement(entity.getPolicies());
        assertTrue(policy instanceof MyPolicy, "policy="+policy);
        assertEquals(policy.getName(), "mypolicyname");
        assertEquals(policy.getConfig(MyPolicy.CONF1), "myconf1val");
    }
    
    @Test
    public void testAddsPolicy() throws Exception {
        MyPolicy policy = new MyPolicy();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(policy));
        
        assertEquals(Iterables.getOnlyElement(entity.getPolicies()), policy);
    }
    
    public static class MyPolicy extends AbstractPolicy {
        public static final BasicConfigKey<String> CONF1 = new BasicConfigKey<String>(String.class, "test.conf1", "my descr, conf1", "defaultval1");
        public static final BasicConfigKey<Integer> CONF2 = new BasicConfigKey<Integer>(Integer.class, "test.conf2", "my descr, conf2", 2);
        
        @SetFromFlag
        public String myfield;
    }
}
