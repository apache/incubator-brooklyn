package brooklyn.rest.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.Catalog;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class BrooklynRestResourceUtilsTest {

    private LocalManagementContext managementContext;
    private BrooklynRestResourceUtils util;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
        util = new BrooklynRestResourceUtils(managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }

    @Test
    public void testCreateAppFromImplClass() {
        ApplicationSpec spec = ApplicationSpec.builder()
                .name("myname")
                .type(MyApplicationImpl.class.getName())
                .locations(ImmutableSet.of("localhost"))
                .build();
        Application app = util.create(spec);
        
        assertEquals(ImmutableList.copyOf(managementContext.getApplications()), ImmutableList.of(app));
        assertEquals(app.getDisplayName(), "myname");
        assertTrue(app instanceof EntityProxy);
        assertTrue(app instanceof MyInterface);
        assertFalse(app instanceof MyApplicationImpl);
    }

    @Test
    public void testNestedApplications() {
        // hierarchy is: app -> subapp -> subentity (where subentity has a policy)
        
        MyApplicationImpl app = new MyApplicationImpl();
        app.setDisplayName("app");
        
        MyApplicationImpl subapp = new MyApplicationImpl();
        subapp.setDisplayName("subapp");
        
        TestEntityImpl subentity = new TestEntityImpl(MutableMap.of("displayName", "subentity"), subapp);
        subentity.addPolicy(new MyPolicy(MutableMap.of("name", "mypolicy")));
        subentity.getApplication(); // force this to be cached
        
        app.addChild(subapp);
        Entities.startManagement(app, managementContext);
        
        EntityLocal subappRetrieved = util.getEntity(app.getId(), subapp.getId());
        assertEquals(subappRetrieved.getDisplayName(), "subapp");
        
        EntityLocal subentityRetrieved = util.getEntity(app.getId(), subentity.getId());
        assertEquals(subentityRetrieved.getDisplayName(), "subentity");
        
        Policy subappPolicy = util.getPolicy(app.getId(), subentity.getId(), "mypolicy");
        assertEquals(subappPolicy.getName(), "mypolicy");
    }

    public interface MyInterface {
    }

    @Catalog
    public static class MyApplicationImpl extends AbstractApplication implements MyInterface {
        @Override
        public void init() {
            // no-op
        }
    }
    
    public static class MyPolicy extends AbstractPolicy {
        public MyPolicy(Map<String, ?> flags) {
            super(flags);
        }
    }
}
