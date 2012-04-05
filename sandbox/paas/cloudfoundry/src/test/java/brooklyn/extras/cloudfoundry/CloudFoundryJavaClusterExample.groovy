package brooklyn.extras.cloudfoundry

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.test.TestUtils

/**
 * example app showing how to start an cloudfoundry java war
 *  
 * if this doesn't start, we may have too many apps, delete some using:
 * vmc apps 
 * vmc delete brooklyn-1234
 * (or online at the cloudfoundry portal)
 * 
 * @author alex
 *
 */
class CloudFoundryJavaClusterExample extends AbstractApplication {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryJavaClusterExample.class)
    
    public static final String WAR_FILE_URL = "classpath://hello-world.war";
                
    CloudFoundryJavaWebAppCluster cloudfoundry = 
      new CloudFoundryJavaWebAppCluster(this, war: WAR_FILE_URL);
    
    // TODO a richer example which starts CloudFoundry alongside Tomcats in EC2 with geoscaling
      
    // ---- the code above is your app descriptor; code below runs it ----
      
    CloudFoundryLocation loc = new CloudFoundryLocation()
      
    public static void main(String[] args) {
        def app = new CloudFoundryJavaClusterExample();
        
        app.start([app.loc]);
        
        log.info "should now be able to visit site (for 2m): {}", app.cloudfoundry.getWebAppAddress()
        //should now be able to visit (assert?)
        
        Thread.sleep(2*60*1000)

        //and kill
        log.info "now cleaning up that app: {}", app.cloudfoundry.getWebAppAddress()
        app.stop()
        
        log.info "finished, should terminate shortly"
    }
}
