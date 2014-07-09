package brooklyn.management.osgi;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.os.Os;
import brooklyn.util.osgi.Osgis;

/** tests some assumptions about OSGi behaviour, in standalone mode (not part of brooklyn).
 * 
 * relies on the following bundles, which exist in the classpath (and contain their sources):
 * 
 * <li>brooklyn-osgi-test-a_0.1.0 -
 *     defines TestA which has a "times" method and a static multiplier field;
 *     we set the multiplier to determine when we are sharing versions and when not
 *     
 *  */
public class OsgiStandaloneTest {

    public static final String BROOKLYN_OSGI_TEST_A_0_1_0_URL = "classpath:///brooklyn/osgi/brooklyn-osgi-test-a_0.1.0.jar";
    
    protected Framework framework = null;
    private File storageTempDir;

    @BeforeMethod
    public void setUp() throws Exception {
        storageTempDir = Os.newTempDir("osgi-standalone");
        framework = Osgis.newFrameworkStarted(storageTempDir.getAbsolutePath(), true, null);
    }

    @AfterMethod
    public void tearDown() throws BundleException, IOException {
        if (framework!=null) {
            framework.stop();
            framework = null;
        }
        if (storageTempDir!=null) {
            FileUtils.deleteDirectory(storageTempDir);
            storageTempDir = null;
        }
    }

    protected Bundle install(String url) throws BundleException {
        try {
            return Osgis.install(framework, url);
        } catch (Exception e) {
            throw new IllegalStateException("test resources not available; may be an IDE issue, so try a mvn rebuild of this project", e);
        }
    }
    
    @Test
    public void testInstallBundle() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
    }

    @Test
    public void testAMultiplier() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 5);
        checkMath(bundle, 3, 15);
    }

    /** run two multiplier tests to ensure that irrespective of order the tests run in, 
     * on a fresh install the multiplier is reset */
    @Test
    public void testANOtherMultiple() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 14);
        checkMath(bundle, 3, 42);
    }

    @Test
    public void testGetBundle() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        setAMultiplier(bundle, 3);

        // can look it up based on the same location string (no other "location identifier" reference string seems to work here, however) 
        Bundle bundle2 = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle2, 3, 9);
    }

    @Test
    public void testUninstallAndReinstallBundle() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 3);
        checkMath(bundle, 3, 9);
        bundle.uninstall();
        
        Bundle bundle2 = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle2, 3, 6);
    }

    protected void checkMath(Bundle bundle, int input, int output) throws Exception {
        Assert.assertNotNull(bundle);
        Class<?> aClass = bundle.loadClass("brooklyn.test.osgi.TestA");
        Object aInst = aClass.newInstance();
        Object result = aClass.getMethod("times", int.class).invoke(aInst, input);
        Assert.assertEquals(result, output);
    }

    protected void setAMultiplier(Bundle bundle, int newMultiplier) throws Exception {
        Assert.assertNotNull(bundle);
        Class<?> aClass = bundle.loadClass("brooklyn.test.osgi.TestA");
        aClass.getField("multiplier").set(null, newMultiplier);
    }
    
}
