package brooklyn.extras.cloudfoundry

import java.util.Collection

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.location.Location

import com.google.common.collect.Iterables

/**
 * example app showing how to start an cloudfoundry java war
 *  
 * if this doesn't start, we may have too many apps, delete some using:
 * <code>
 * vmc apps 
 * vmc delete brooklyn-1234
 * </code>
 * (or online at the cloudfoundry portal)
 * or
 * <code>
 * for x in `vmc apps | grep brooklyn | awk '{ print $2 }'` ; do vmc delete $x ; done
 * </code>
 * @author alex
 *
 */
class CloudFoundryJavaClusterFromLocationExample extends AbstractApplication {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryJavaClusterFromLocationExample.class)
    
    public static final String WAR_FILE_URL = "classpath://hello-world.war";
                
    ElasticJavaWebAppService svc;
    
    @Override
    public void preStart(Collection<? extends Location> locations) {
        svc = new ElasticJavaWebAppService.Factory().newFactoryForLocation( Iterables.getOnlyElement(locations) ).
            newEntity(this, war: WAR_FILE_URL);
    }  
    
    // the code above is your app descriptor; note, no mention of cloudfoundry
    // code below runs it
      
      
    public static void main(String[] args) {
        def app = new CloudFoundryJavaClusterFromLocationExample();
        
        app.start([new CloudFoundryLocation(target: 'api.cloudfoundry.com')]);
        
        log.info "should now be able to visit site (for 2m): {}", app.svc.getAttribute(ElasticJavaWebAppService.ROOT_URL)
        //should now be able to visit (assert?)
        
        for (int i=0; i<2*6; i++) {
            //periodically print stats
            Entities.dumpInfo(app);
            Thread.sleep(10*1000);
        }

        //and kill
        log.info "now cleaning up that app: {}", app
        app.stop()
        
        log.info "finished, should terminate shortly"
    }
}
