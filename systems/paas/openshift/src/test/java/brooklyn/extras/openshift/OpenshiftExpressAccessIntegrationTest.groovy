package brooklyn.extras.openshift

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.extras.openshift.OpenshiftExpressAccess.Cartridge

class OpenshiftExpressAccessIntegrationTest {

    //we assume the test KeepForUnitTest1 has been created, with the default contents
    
//    rhc-create-app -a KeepForUnitTest1 -t jbossas-7.0 -l openshift@cloudsoftcorp.com -p 0penshift
//    http://KeepForUnitTest1-brooklyn.rhcloud.com/
//    ssh://4d9495c6ad4b40c98afdbc40367ab367@KeepForUnitTest1-brooklyn.rhcloud.com/~/git/KeepForUnitTest1.git/
    
    static final Logger log = LoggerFactory.getLogger(OpenshiftExpressAccessIntegrationTest.class)
    
    public static final String TEST_USER = "openshift@cloudsoftcorp.com",
        TEST_PASSWORD = "0penshift",
        TEST_APP = "KeepForUnitTest1";
    
    @Test(groups = [ "Integration", "WIP" ])
    public void testCanGetUserInfo() {
        def osa = new OpenshiftExpressAccess(username:TEST_USER, password:TEST_PASSWORD);
        def result = osa.getUserInfo();
        assertEquals result.data.user_info.rhlogin, TEST_USER
        assertEquals result.data.user_info.namespace, "brooklyn"
        
        def apps = osa.getUserInfo().data.app_info;
        assertNotNull apps[TEST_APP]
        assertEquals apps[TEST_APP].framework, Cartridge.JBOSS_AS_7.toString();
    }

    
    @Test(groups = [ "Integration", "WIP" ])
    public void testCanCreateDestroyApp() {
        String appName = "UnitTestTemp"+((int)(10000*Math.random()));
        def app = new OpenshiftExpressApplicationAccess(username:TEST_USER, password:TEST_PASSWORD, appName: appName, debug:true);
        log.info "WORKING WITH NEW APP: "+app.appName
        log.info "configure: "+app.create()
        log.info "status: "+app.status()
        log.info "deconfigure: "+app.destroy()
    }
    
    //TODO start, stop app
}
