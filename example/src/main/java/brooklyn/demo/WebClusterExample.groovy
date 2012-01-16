package brooklyn.demo

import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.policy.ResizerPolicy
import brooklyn.util.internal.TimeExtras

/**
 * Run with:
 *   java -Xmx512m -Xms128m -XX:MaxPermSize=256m -cp target/brooklyn-example-0.2.0-SNAPSHOT-with-dependencies.jar brooklyn.demo.WebClusterExample 
 **/
public class WebClusterExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExample)
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]

    public static final String WAR_PATH = "classpath://hello-world.war"
    
    public WebClusterExample(Map props=[:]) {
        super(props)
        setConfig(JavaWebAppService.ROOT_WAR, WAR_PATH)
    }

    NginxController nginxController = new NginxController( 
        domain:'brooklyn.geopaas.org',
        port:8000 )

    ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(this,
        name:"WebApp cluster",
        controller:nginxController,
        initialSize: 1,
        webServerFactory: this.&newWebServer )

    ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
        setSizeRange(1, 5).
        setMetricRange(10, 100);
    
    protected JavaWebAppService newWebServer(Map flags, Entity cluster) {
        return new JBoss7Server(flags, cluster).configure(httpPort: "8080+")
    }

    public static void main(String[] argv) {
        List<Location> locations = 
            Locations.getLocationsById(Arrays.asList(argv) ?: DEFAULT_LOCATIONS)

        WebClusterExample app = new WebClusterExample(name:'Brooklyn WebApp Cluster example')
            
        BrooklynLauncher.manage(app)
        app.start(locations)
        Entities.dumpInfo(app)
        
        app.webCluster.cluster.addPolicy(app.policy)
    }
    
}
