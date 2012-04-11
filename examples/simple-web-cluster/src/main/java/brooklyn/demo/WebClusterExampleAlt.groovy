package brooklyn.demo

import groovy.transform.InheritConstructors

import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.CommandLineLocations
import brooklyn.location.basic.LocationRegistry
import brooklyn.policy.ResizerPolicy
import brooklyn.util.CommandLineUtil

/**
 * Launches a clustered and load-balanced set of web servers.
 * (Simplified variant of WebClusterExample.)
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath. 
 **/
@InheritConstructors
public class WebClusterExampleAlt extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExampleAlt)
    
    public static final String DEFAULT_LOCATION = "localhost"
    public static final String WAR_PATH = "classpath://hello-world-webapp.war"
    

    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this, war: WAR_PATH);
    {
        web.addPolicy(new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
            setSizeRange(1, 5).
            setMetricRange(10, 100));
    }
    

    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])

        WebClusterExampleAlt app = new WebClusterExampleAlt(name:'Brooklyn WebApp Cluster example')
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        
        Entities.dumpInfo(app)
    }
    
}
