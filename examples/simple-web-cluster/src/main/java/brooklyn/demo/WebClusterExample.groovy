package brooklyn.demo

import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.CommandLineLocations
import brooklyn.location.basic.LocationRegistry
import brooklyn.policy.ResizerPolicy
import brooklyn.util.CommandLineUtil

/**
 * Launches a clustered and load-balanced set of web servers.
 * Demonstrates syntax, so many of the options used here are the defaults.
 * (So the class could be much simpler, as in WebClusterExampleAlt.)
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath. 
 **/
public class WebClusterExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExample)
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final List<String> DEFAULT_LOCATION = [ CommandLineLocations.LOCALHOST ]

    public static final String WAR_PATH = "classpath://hello-world-webapp.war"
    
    public WebClusterExample(Map props=[:]) {
        super(props)
    }
    

    NginxController nginxController = new NginxController(
        domain: 'webclusterexample.brooklyn.local',
        port:8000)
    
    JBoss7ServerFactory jbossFactory = new JBoss7ServerFactory(httpPort: "8080+", war: WAR_PATH); 

    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this,
        name: "WebApp cluster",
        controller: nginxController,
        initialSize: 1,
        factory: jbossFactory)
    
    ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
        setSizeRange(1, 5).
        setMetricRange(10, 100);
    

    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])

        WebClusterExample app = new WebClusterExample(name:'Brooklyn WebApp Cluster example')
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
        
        app.web.cluster.addPolicy(app.policy)
    }

}
