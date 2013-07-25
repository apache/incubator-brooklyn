package brooklyn.entity;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Runs a test with many different distros and versions.
 */
public abstract class AbstractEc2LiveTest {
    
    // FIXME Currently have just focused on test_Debian_6; need to test the others as well!

    // TODO No nice fedora VMs
    
    // TODO Instead of this sub-classing approach, we could use testng's "provides" mechanism
    // to say what combo of provider/region/flags should be used. The problem with that is the
    // IDE integration: one can't just select a single test to run.
    
    public static final String PROVIDER = "aws-ec2";
    public static final String REGION_NAME = "us-east-1";
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String TINY_HARDWARE_ID = "t1.micro";
    public static final String SMALL_HARDWARE_ID = "m1.small";
    
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
    public void test_Debian_6() throws Exception {
        // release codename "squeeze"
        // Image: {id=us-east-1/ami-7ce17315, providerId=ami-7ce17315, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=debian, arch=paravirtual, version=6.0, description=Debian 6.0.7 (Squeeze),  is64Bit=true}, description=Debian 6.0.7 (Squeeze), version=20091011, status=AVAILABLE[available], loginUser=ubuntu, userMetadata={owner=379101102735, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-7ce17315", "loginUser", "admin", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_10_0() throws Exception {
        // Image: {id=us-east-1/ami-5e008437, providerId=ami-5e008437, name=RightImage_Ubuntu_10.04_x64_v5.8.8.3, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=10.04, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, version=5.8.8.3, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-5e008437", "loginUser", "root", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_12_0() throws Exception {
        // Image: {id=us-east-1/ami-950680fc, providerId=ami-950680fc, name=RightImage_Ubuntu_12.04_x64_v5.8.8, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=12.04, description=rightscale-us-east/RightImage_Ubuntu_12.04_x64_v5.8.8.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_Ubuntu_12.04_x64_v5.8.8.manifest.xml, version=5.8.8, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-950680fc", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_CentOS_6_3() throws Exception {
        // Image: {id=us-east-1/ami-7d7bfc14, providerId=ami-7d7bfc14, name=RightImage_CentOS_6.3_x64_v5.8.8.5, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.0, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, version=5.8.8.5, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-7d7bfc14", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_CentOS_5_6() throws Exception {
        // Image: {id=us-east-1/ami-49e32320, providerId=ami-49e32320, name=RightImage_CentOS_5.6_x64_v5.7.14, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=5.6, description=rightscale-us-east/RightImage_CentOS_5.6_x64_v5.7.14.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_5.6_x64_v5.7.14.manifest.xml, version=5.7.14, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-49e32320", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        // Image: {id=us-east-1/ami-d258fbbb, providerId=ami-d258fbbb, name=RHEL-6.3-Starter-i386-1-Hourly2, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=rhel, arch=paravirtual, version=6.4, description=, is64Bit=true}, description=, version=6.3, status=AVAILABLE[available], loginUser=root, userMetadata={owner=309956199498, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-d258fbbb", "hardwareId", SMALL_HARDWARE_ID));
    }
    
    protected void runTest(Map<String,?> flags) throws Exception {
        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(getClass().getName()))
                .putAll(flags)
                .build();
        jcloudsLocation = ctx.getLocationRegistry().resolve(LOCATION_SPEC, allFlags);

        doTest(jcloudsLocation);
    }
    
    protected abstract void doTest(Location loc) throws Exception;
}
