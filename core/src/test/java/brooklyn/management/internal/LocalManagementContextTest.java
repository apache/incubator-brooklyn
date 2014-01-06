package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.Location;

public class LocalManagementContextTest {
    
    private File localPropertiesFile;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        localPropertiesFile = File.createTempFile("local-brooklyn-properties-test", ".properties");
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (localPropertiesFile != null) localPropertiesFile.delete();
    }
    
    @Test
    public void testReloadProperties() {
        LocalManagementContext context = new LocalManagementContext();
        Location location = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        context.reloadBrooklynProperties();
        Location location2 = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        assertEquals(location2.getDisplayName(), "myname2");
    }
    
}
