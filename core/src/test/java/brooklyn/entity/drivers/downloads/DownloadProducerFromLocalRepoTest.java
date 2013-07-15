package brooklyn.entity.drivers.downloads;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DownloadProducerFromLocalRepoTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private String entitySimpleType;
    private DownloadProducerFromLocalRepo resolver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        managementContext = new LocalManagementContext(brooklynProperties);
        
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
        entitySimpleType = TestEntity.class.getSimpleName();
        
        resolver = new DownloadProducerFromLocalRepo(brooklynProperties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testReturnsEmptyWhenDisabled() throws Exception {
        brooklynProperties.put(DownloadProducerFromLocalRepo.LOCAL_REPO_ENABLED, false);
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
        String entityVersion = "myversion";
        String filename = "my.file.name";
        entity.setAttribute(Attributes.VERSION, entityVersion);
        
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver, ImmutableMap.of("filename", filename));
        assertResolves(req, String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, filename));
    }
    
    @Test
    public void testReturnsFileSuffixFromRequirements() throws Exception {
        // uses ${driver.downloadFileSuffix}
        String entityVersion = "myversion";
        String fileSuffix = "mysuffix";
        String expectedFilename = (entitySimpleType+"-"+entityVersion+"."+fileSuffix).toLowerCase();
        entity.setAttribute(Attributes.VERSION, entityVersion);
        
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver, ImmutableMap.of("fileSuffix", fileSuffix));
        assertResolves(req, String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, expectedFilename));
    }
    
    private void assertResolves(String... expected) {
        assertResolves(ImmutableList.copyOf(expected), ImmutableList.<String>of());
    }
    
    private void assertResolves(List<String> expectedPrimaries, List<String> expectedFallbacks) {
        assertResolves(new BasicDownloadRequirement(driver), expectedPrimaries, expectedFallbacks);
    }
    
    private void assertResolves(DownloadRequirement req, String... expected) {
        assertResolves(req, ImmutableList.copyOf(expected), ImmutableList.<String>of());
    }

    private void assertResolves(DownloadRequirement req, List<String> expectedPrimaries, List<String> expectedFallbacks) {
        DownloadTargets actual = resolver.apply(req);
        assertEquals(actual.getPrimaryLocations(), expectedPrimaries);
        assertEquals(actual.getFallbackLocations(), expectedFallbacks);
    }
}
