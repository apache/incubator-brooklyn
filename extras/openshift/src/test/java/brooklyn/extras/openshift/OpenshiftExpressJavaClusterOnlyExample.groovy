package brooklyn.extras.openshift

import brooklyn.entity.basic.AbstractApplication
import brooklyn.test.TestUtils

/**
 * example app showing how to start an openshift java war
 *  
 * if this doesn't start, we may have too many apps, delete some using:
 * rhc-user-info -l openshift@cloudsoftcorp.com -p 0penshift 
 * rhc-ctl-app -l openshift@cloudsoftcorp.com -p 0penshift -a Brooklyn4f9b7369 -c destroy
 * (or online at the openshift express portal)
 * 
 * @author alex
 *
 */
class OpenshiftExpressJavaClusterOnlyExample extends AbstractApplication {

    File warFile = TestUtils.getResource("hello-world.war", getClass().getClassLoader())
                
    OpenshiftExpressJavaWebAppCluster openshift = 
      new OpenshiftExpressJavaWebAppCluster(this, war: warFile.getAbsolutePath());
    
    // TODO a richer example which starts Openshift alongside Amazon JBosses with geoscaling
    // TODO (shouldn't use the tomcat-branded hello world for this :)
      
    // ---- the code above is your app descriptor; code below runs it ----
      
    OpenshiftLocation loc = new OpenshiftLocation(
          username: OpenshiftExpressAccessIntegrationTest.TEST_USER,
          password: OpenshiftExpressAccessIntegrationTest.TEST_PASSWORD)
      
    public static void main(String[] args) {
        def app = new OpenshiftExpressJavaClusterOnlyExample();
        
        app.start([app.loc]);
        
        println "should now be able to visit: "+app.openshift.getWebAppAddress()
        //should now be able to visit (assert?)
        
        Thread.sleep(60*1000)

        //and kill  (destroy semantics are TBC)
        println "now cleaning up that app: "+app.openshift.getWebAppAddress()
        app.openshift.destroy()
        app.destroy()
    }
}
