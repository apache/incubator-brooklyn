package brooklyn.entity;

import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Runs a test with many different distros and versions.
 */
public abstract class AbstractSoftlayerLiveTest {
    
    // TODO See todos in AbstractEc2LiveTest
    
    public static final String PROVIDER = "softlayer";
    public static final String REGION_NAME = "265592"; // Amsterdam 1
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String SMALL_RAM = "3";
    
    protected BrooklynProperties brooklynProperties;
    protected ManagementContext ctx;
    
    protected TestApplication app;
    protected Location jcloudsLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        List<String> propsToRemove = ImmutableList.of("imageId", "imageDescriptionRegex", "imageNameRegex", "inboundPorts", "hardwareId", "minRam");

        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        for (String propToRemove : propsToRemove) {
            for (String propVariant : ImmutableList.of(propToRemove, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, propToRemove))) {
                brooklynProperties.remove("brooklyn.locations.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.locations."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+propVariant);
            }
        }

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        
        ctx = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, ctx);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    // FIXME need a jclouds-softlayer 1.6.1-cloudsoft.2; see https://github.com/jclouds/jclouds/pull/77
    @Test(groups = {"Live"})
    public void test_Ubuntu_12_0_4() throws Exception {
        // Image: {id=17446, providerId=17446, os={family=ubuntu, version=12.04, description=Ubuntu Linux 12.04 LTS Precise Pangolin - Minimal Install (64 bit), is64Bit=true}, description=Ubuntu Linux 12.04 LTS Precise Pangolin - Minimal Install (64 bit), status=AVAILABLE, loginUser=root}
        runTest(ImmutableMap.<String,Object>of("imageId", "17446"/*, "minRam", SMALL_RAM*/));
    }

    // FIXME need a jclouds-softlayer 1.6.1-cloudsoft.2; see https://github.com/jclouds/jclouds/pull/77
    @Test(groups = {"Live"})
    public void test_Centos_6_0() throws Exception {
        // Image: {id=13945, providerId=13945, os={family=centos, version=6.0, description=CentOS 6.0 - Minimal Install (64 bit), is64Bit=true}, description=CentOS 6.0 - Minimal Install (64 bit), status=AVAILABLE, loginUser=root}
        runTest(ImmutableMap.<String,Object>of("imageId", "13945"));
    }
    
    protected void runTest(Map<String,?> flags) throws Exception {
        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(getClass().getName()))
                .put("vmNameMaxLength", 30)
                .putAll(flags)
                .build();
        jcloudsLocation = ctx.getLocationRegistry().resolve(LOCATION_SPEC, allFlags);

        doTest(jcloudsLocation);
    }
    
    protected abstract void doTest(Location loc) throws Exception;
}
