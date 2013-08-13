package brooklyn.entity.drivers.downloads;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class BasicDownloadsRegistryTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        managementContext = new LocalManagementContext(brooklynProperties);

        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
        
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        LocalManagementContext.terminateAll();
    }

    @Test
    public void testUsesDownloadUrlAttribute() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "acme.com/version=${version},type=${type},simpletype=${simpletype}");
        String expectedFilename = String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), "TestEntity");
        
        String expectedLocalRepo = String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", "TestEntity", "myversion", expectedFilename);
        String expectedDownloadUrl = String.format("acme.com/%s", expectedFilename);
        String expectedCloudsoftRepo = String.format("http://downloads.cloudsoftcorp.com/brooklyn/repository/%s/%s/%s", "TestEntity", "myversion", expectedFilename);
        assertResolves(expectedLocalRepo, expectedDownloadUrl, expectedCloudsoftRepo);
    }
    
    @Test
    public void testUsesDownloadAddonUrlsAttribute() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myentityversion");
        entity.setAttribute(Attributes.DOWNLOAD_ADDON_URLS, ImmutableMap.of("myaddon", "acme.com/addon=${addon},version=${addonversion},type=${type},simpletype=${simpletype}"));
        String expectedFilename = String.format("addon=%s,version=%s,type=%s,simpletype=%s", "myaddon", "myaddonversion", TestEntity.class.getName(), "TestEntity");
        
        String expectedLocalRepo = String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", "TestEntity", "myentityversion", expectedFilename);
        String expectedDownloadUrl = String.format("acme.com/%s", expectedFilename);
        String expectedCloudsoftRepo = String.format("http://downloads.cloudsoftcorp.com/brooklyn/repository/%s/%s/%s", "TestEntity", "myentityversion", expectedFilename);
        DownloadResolver actual = managementContext.getEntityDownloadsManager().newDownloader(driver, "myaddon", ImmutableMap.of("addonversion", "myaddonversion"));
        assertEquals(actual.getTargets(), ImmutableList.of(expectedLocalRepo, expectedDownloadUrl, expectedCloudsoftRepo), "actual="+actual);
    }
    
    @Test
    public void testDefaultResolverSubstitutesDownloadUrlFailsIfVersionMissing() throws Exception {
        entity.setAttribute(Attributes.DOWNLOAD_URL, "version=${version}");
        try {
            DownloadResolver result = managementContext.getEntityDownloadsManager().newDownloader(driver);
            fail("Should have failed, but got "+result);
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("${version}")) throw e;
        }
    }
    
    @Test
    public void testReturnsLocalRepoThenOverrideThenAttributeValThenCloudsoftUrlThenFallback() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "http://fromprops/${version}.allprimary");
        brooklynProperties.put("brooklyn.downloads.all.fallbackurl", "http://fromfallback/${version}.allfallback");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "http://fromattrib/${version}.default");
        entity.setConfig(ConfigKeys.SUGGESTED_VERSION, "myversion");
        String expectedFilename = "myversion.allprimary";

        String expectedLocalRepo = String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", "TestEntity", "myversion", expectedFilename);
        String expectedDownloadUrl = String.format("http://fromattrib/%s", "myversion.default");
        String expectedCloudsoftRepo = String.format("http://downloads.cloudsoftcorp.com/brooklyn/repository/%s/%s/%s", "TestEntity", "myversion", expectedFilename);
        assertResolves(
                expectedLocalRepo,
                "http://fromprops/myversion.allprimary", 
                expectedDownloadUrl, 
                expectedCloudsoftRepo, 
                "http://fromfallback/myversion.allfallback");
    }

    @Test
    public void testInfersFilenameFromDownloadUrl() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "http://myhost.com/myfile-${version}.tar.gz");

        DownloadResolver actual = managementContext.getEntityDownloadsManager().newDownloader(driver);
        assertEquals(actual.getFilename(), "myfile-myversion.tar.gz");
    }
    
    @Test
    public void testInfersAddonFilenameFromDownloadUrl() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        entity.setAttribute(Attributes.DOWNLOAD_ADDON_URLS, ImmutableMap.of("myaddon", "http://myhost.com/myfile-${addonversion}.tar.gz"));

        DownloadResolver actual = managementContext.getEntityDownloadsManager().newDownloader(driver, "myaddon", ImmutableMap.of("addonversion", "myaddonversion"));
        assertEquals(actual.getFilename(), "myfile-myaddonversion.tar.gz");
    }
    
    @Test
    public void testCanOverrideFilenameFromDownloadUrl() throws Exception {
        entity.setAttribute(Attributes.VERSION, "myversion");
        entity.setAttribute(Attributes.DOWNLOAD_URL, "http://myhost.com/download/");

        DownloadResolver actual = managementContext.getEntityDownloadsManager().newDownloader(driver, ImmutableMap.of("filename", "overridden.filename.tar.gz"));
        assertEquals(actual.getFilename(), "overridden.filename.tar.gz");
    }
    
    private void assertResolves(String... expected) {
        DownloadResolver actual = managementContext.getEntityDownloadsManager().newDownloader(driver);
        assertEquals(actual.getTargets(), ImmutableList.copyOf(expected), "actual="+actual);
    }
}
