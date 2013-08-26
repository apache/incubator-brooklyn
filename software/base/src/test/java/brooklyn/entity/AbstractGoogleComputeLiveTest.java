package brooklyn.entity;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Runs a test with many different distros and versions.
 */
public abstract class AbstractGoogleComputeLiveTest {
    
    // FIXME Currently have just focused on test_Debian_6; need to test the others as well!

    // TODO No nice fedora VMs
    
    // TODO Instead of this sub-classing approach, we could use testng's "provides" mechanism
    // to say what combo of provider/region/flags should be used. The problem with that is the
    // IDE integration: one can't just select a single test to run.
    
    public static final String PROVIDER = "google-compute-engine";
    public static final String REGION_NAME = null;//"us-central1";
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String STANDARD_HARDWARE_ID = "us-central1-b/n1-standard-1-d";
    private static final int MAX_TAG_LENGTH = 63;

    protected BrooklynProperties brooklynProperties;
    protected ManagementContext ctx;
    
    protected TestApplication app;
    protected Location jcloudsLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-description-regex");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-name-regex");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-id");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".inboundPorts");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".hardware-id");

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        
        ctx = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, ctx);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = {"Live"})
    public void test_GCEL_10_04() throws Exception {
        // release codename "squeeze"
        runTest(ImmutableMap.of("imageId", "gcel-10-04-v20130325", "loginUser", "admin", "hardwareId", STANDARD_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_GCEL_12_04() throws Exception {
        // release codename "squeeze"
        runTest(ImmutableMap.of("imageId", "gcel-12-04-v20130325", "loginUser", "admin", "hardwareId", STANDARD_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_10_04() throws Exception {
        // release codename "squeeze"
        runTest(ImmutableMap.of("imageId", "ubuntu-10-04-v20120912", "loginUser", "admin", "hardwareId", STANDARD_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_12_04() throws Exception {
        // release codename "squeeze"
        runTest(ImmutableMap.of("imageId", "ubuntu-10-04-v20120912", "loginUser", "admin", "hardwareId", STANDARD_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_CentOS_6() throws Exception {
        runTest(ImmutableMap.of("imageId", "centos-6-v20130325", "hardwareId", STANDARD_HARDWARE_ID));
    }

    protected void runTest(Map<String,?> flags) throws Exception {
        String tag = getClass().getSimpleName().toLowerCase();
        int length = tag.length();
        if (length > MAX_TAG_LENGTH)
            tag = tag.substring(length - MAX_TAG_LENGTH, length);
        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(tag))
                .putAll(flags)
                .build();
        jcloudsLocation = ctx.getLocationRegistry().resolve(LOCATION_SPEC, allFlags);

        doTest(jcloudsLocation);
    }

    protected abstract void doTest(Location loc) throws Exception;
}
