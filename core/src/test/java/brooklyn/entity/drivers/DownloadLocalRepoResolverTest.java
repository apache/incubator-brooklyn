package brooklyn.entity.drivers;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.DownloadsRegistry.DownloadTargets;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;

import com.google.common.collect.ImmutableList;

public class DownloadLocalRepoResolverTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private String entitySimpleType;
    private DownloadLocalRepoResolver resolver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        managementContext = new LocalManagementContext(brooklynProperties);
        
        loc = new SimulatedLocation();
        app = ApplicationBuilder.builder(TestApplication.class).manage(managementContext);
        entity = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
        entitySimpleType = TestEntity.class.getSimpleName();
        
        resolver = new DownloadLocalRepoResolver(brooklynProperties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    @Test
    public void testReturnsEmptyWhenDisabled() throws Exception {
        brooklynProperties.put(DownloadLocalRepoResolver.LOCAL_REPO_ENABLED_PROPERTY, "false");
        assertResolves(ImmutableList.<String>of(), ImmutableList.<String>of());
    }
    
    @Test
    public void testReturnsDefault() throws Exception {
        // uses default of ${simpletype}-${version}.tar.gz";
        String entityVersion = "myversion";
        String downloadFilename = (entitySimpleType+"-"+entityVersion+".tar.gz").toLowerCase();
        entity.setAttribute(Attributes.VERSION, entityVersion);
        assertResolves(String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, downloadFilename));
    }
    
    @Test
    public void testReturnsFilenameFromDriver() throws Exception {
        // uses ${driver.downloadFilename}
        String entityVersion = "myversion";
        String downloadFilename = "my.file.name";
        entity.setAttribute(Attributes.VERSION, entityVersion);
        driver.setFlag("downloadFilename", downloadFilename);
        assertResolves(String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, downloadFilename));
    }
    
    @Test
    public void testReturnsFileSuffixFromDriver() throws Exception {
        // uses ${driver.downloadFileSuffix}
        String entityVersion = "myversion";
        String downloadFileSuffix = "mysuffix";
        String downloadFilename = (entitySimpleType+"-"+entityVersion+"."+downloadFileSuffix).toLowerCase();
        entity.setAttribute(Attributes.VERSION, entityVersion);
        driver.setFlag("downloadFileSuffix", downloadFileSuffix);
        assertResolves(String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, downloadFilename));
    }
    
    private void assertResolves(String... expected) {
        assertResolves(ImmutableList.copyOf(expected), ImmutableList.<String>of());
    }
    
    private void assertResolves(List<String> expectedPrimaries, List<String> expectedFallbacks) {
        DownloadTargets actual = resolver.apply(driver);
        assertEquals(actual.getPrimaryLocations(), expectedPrimaries);
        assertEquals(actual.getFallbackLocations(), expectedFallbacks);
    }
}
