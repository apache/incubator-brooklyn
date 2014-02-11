package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SameServerEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.OsDetails;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindJcloudsLocationLiveTest extends AbstractJcloudsTest {

    public static final String AWS_EC2_REGION_NAME = "us-east-1";
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + (AWS_EC2_REGION_NAME == null ? "" : ":" + AWS_EC2_REGION_NAME);

    private ClassLoader classLoader = getClass().getClassLoader();
    private TestApplication origApp;
    private SameServerEntity origSameServerE;
    private TestEntity origE;
    private TestApplication newApp;
    private File mementoDir;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), managementContext);
        origSameServerE = origApp.createAndManageChild(EntitySpec.create(SameServerEntity.class));
        origE = origSameServerE.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(origE);
        
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        jcloudsLocation.setConfig(JcloudsLocation.HARDWARE_ID, AWS_EC2_SMALL_HARDWARE_ID);
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
//        super.tearDown();
//        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
//        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Override
    protected LocalManagementContext newManagementContext() {
        mementoDir = Files.createTempDir();
        return RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
    }
    
    @Test
    public void testRebindsToJcloudsMachine() throws Exception {
        origApp.start(ImmutableList.of(jcloudsLocation));
        JcloudsLocation origJcloudsLocation = jcloudsLocation;
        JcloudsSshMachineLocation origMachine = (JcloudsSshMachineLocation) Iterables.find(origE.getLocations(), Predicates.instanceOf(JcloudsSshMachineLocation.class));
        
        newApp = (TestApplication) rebind();
        SameServerEntity newSameServerE = (SameServerEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(SameServerEntity.class));
        TestEntity newE = (TestEntity) Iterables.find(newSameServerE.getChildren(), Predicates.instanceOf(TestEntity.class));
        JcloudsSshMachineLocation newMachine = (JcloudsSshMachineLocation) Iterables.find(newE.getLocations(), Predicates.instanceOf(JcloudsSshMachineLocation.class));
        
        assertMachineEquals(newMachine, origMachine);
        assertTrue(newMachine.isSshable());
        
        JcloudsLocation newJcloudsLoction = newMachine.getParent();
        assertJcloudsLocationEquals(newJcloudsLoction, origJcloudsLocation);
    }
    
    private void assertMachineEquals(JcloudsSshMachineLocation actual, JcloudsSshMachineLocation expected) {
        String errmsg = "actual="+actual.toVerboseString()+"; expected="+expected.toVerboseString();
        assertEquals(actual.getId(), expected.getId(), errmsg);
        assertEquals(actual.getJcloudsId(), expected.getJcloudsId(), errmsg);
        assertOsDetailEquals(actual.getOsDetails(), expected.getOsDetails());
        assertEquals(actual.getSshHostAndPort(), expected.getSshHostAndPort());
        assertEquals(actual.getPrivateAddress(), expected.getPrivateAddress());
        assertEquals(actual.getAllConfigBag().getAllConfig(), expected.getAllConfigBag().getAllConfig(), errmsg);
    }

    private void assertOsDetailEquals(OsDetails actual, OsDetails expected) {
        String errmsg = "actual="+actual+"; expected="+expected;
        if (actual == null) assertNull(expected, errmsg);
        assertEquals(actual.isWindows(), expected.isWindows());
        assertEquals(actual.isLinux(), expected.isLinux());
        assertEquals(actual.isMac(), expected.isMac());
        assertEquals(actual.getName(), expected.getName());
        assertEquals(actual.getArch(), expected.getArch());
        assertEquals(actual.getVersion(), expected.getVersion());
        assertEquals(actual.is64bit(), expected.is64bit());
    }

    private void assertJcloudsLocationEquals(JcloudsLocation actual, JcloudsLocation expected) {
        String errmsg = "actual="+actual.toVerboseString()+"; expected="+expected.toVerboseString();
        assertEquals(actual.getId(), expected.getId(), errmsg);
        assertEquals(actual.getProvider(), expected.getProvider(), errmsg);
        assertEquals(actual.getRegion(), expected.getRegion(), errmsg);
        assertEquals(actual.getIdentity(), expected.getIdentity(), errmsg);
        assertEquals(actual.getCredential(), expected.getCredential(), errmsg);
        assertEquals(actual.getHostGeoInfo(), expected.getHostGeoInfo(), errmsg);
        assertEquals(actual.getAllConfigBag().getAllConfig(), expected.getAllConfigBag().getAllConfig(), errmsg);
    }


    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
