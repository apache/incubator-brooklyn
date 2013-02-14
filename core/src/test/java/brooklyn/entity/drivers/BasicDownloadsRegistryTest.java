package brooklyn.entity.drivers;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class BasicDownloadsRegistryTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private DownloadPropertiesResolver resolver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        managementContext = new LocalManagementContext(brooklynProperties);

        loc = new SimulatedLocation();
        app = ApplicationBuilder.builder(TestApplication.class).manage(managementContext);
        entity = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
        
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    @Test
    public void testReturnsLocalRepoThenOverrideThenAttributeValThenFallback() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "http://fromprops/${version}.1");
        brooklynProperties.put("brooklyn.downloads.all.fallbackurl", "http://fromfallback/${version}.2");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "http://fromattrib/${version}.3");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        
        String expectedLocalRepo = String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", "TestEntity", "myversion", "testentity-myversion.tar.gz");
        assertResolves(expectedLocalRepo, "http://fromprops/myversion.1", "http://fromattrib/myversion.3", "http://fromfallback/myversion.2");
    }

    private void assertResolves(String... expected) {
        List<String> actual = managementContext.getEntityDownloadsRegistry().resolve(driver);
        assertEquals(actual, ImmutableList.copyOf(expected), "actual="+actual);
    }
}
