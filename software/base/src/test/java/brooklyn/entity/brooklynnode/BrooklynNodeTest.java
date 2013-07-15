package brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

public class BrooklynNodeTest {

    // TODO Need test for copying/setting classpath
    
    private static final File BROOKLYN_PROPERTIES_PATH = new File(System.getProperty("user.home")+"/.brooklyn/brooklyn.properties");
    private static final File BROOKLYN_PROPERTIES_BAK_PATH = new File(BROOKLYN_PROPERTIES_PATH+".test.bak");
    
    private TestApplication app;
    private SshMachineLocation loc;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc = new SshMachineLocation(MutableMap.of("address", "localhost"));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testGeneratesCorrectSnapshotDownload() throws Exception {
        String version = "0.6.0-SNAPSHOT";
        String expectedUrl = "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v="+version+"&a=brooklyn-dist&c=dist&e=tar.gz";
        runTestGeneratesCorrectDownloadUrl(version, expectedUrl);
    }
    
    @Test
    public void testGeneratesCorrectReleaseDownload() throws Exception {
        String version = "0.5.0";
        String expectedUrl = "http://search.maven.org/remotecontent?filepath=io/brooklyn/brooklyn-dist/"+version+"/brooklyn-dist-"+version+"-dist.tar.gz";
        runTestGeneratesCorrectDownloadUrl(version, expectedUrl);
    }
    
    private void runTestGeneratesCorrectDownloadUrl(String version, String expectedUrl) throws Exception {
        BrooklynNodeImpl entity = new BrooklynNodeImpl();
        entity.configure(MutableMap.of("version", version));
        entity.setParent(app);
        Entities.manage(entity);
        ConfigToAttributes.apply(entity);
        BrooklynNodeSshDriver driver = new BrooklynNodeSshDriver(entity, loc);
        
        DownloadResolver resolver = ((EntityLocal)entity).getManagementContext().getEntityDownloadsManager().newDownloader(driver);
        List<String> urls = resolver.getTargets();
        
        System.out.println("urls="+urls);
        assertTrue(urls.contains(expectedUrl), "urls="+urls);
    }
}
