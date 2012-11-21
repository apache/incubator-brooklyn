package brooklyn.rest.resources;

import java.util.Collections;

import junit.framework.Assert;

import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.domain.ScriptExecutionSummary;
import brooklyn.rest.testing.mocks.RestMockApp;

public class ScriptResourceTest {

    @Test
    public void testGroovy() {
        LocalManagementContext mgmt = new LocalManagementContext();
        RestMockApp app = new RestMockApp();
        mgmt.manage(app);
        Entities.start(app, Collections.<Location>emptyList());
        
        ScriptResource s = new ScriptResource();
        s.injectManagementContext(mgmt);
        
        ScriptExecutionSummary result = s.groovy(null, "def apps = []; mgmt.applications.each { println 'app:'+it; apps << it.id }; apps");
        Assert.assertEquals(Collections.singletonList(app.getId()).toString(), result.getResult());
        Assert.assertTrue(result.getStdout().contains("app:RestMockApp"));
    }
    
}
