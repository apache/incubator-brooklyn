package brooklyn.rest.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.Catalog;
import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.domain.ApplicationSpec;

import com.google.common.collect.ImmutableList;
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
                .type(MyApplicationImpl.class)
                .locations(ImmutableSet.of("localhost"))
                .build();
        Application app = util.create(spec);
        
        assertEquals(ImmutableList.copyOf(managementContext.getApplications()), ImmutableList.of(app));
        assertEquals(app.getDisplayName(), "myname");
        assertTrue(app instanceof EntityProxy);
        assertTrue(app instanceof MyInterface);
        assertFalse(app instanceof MyApplicationImpl);
    }

    public interface MyInterface {
    }

    @Catalog
    public static class MyApplicationImpl extends AbstractApplication implements MyInterface {
    }
}
