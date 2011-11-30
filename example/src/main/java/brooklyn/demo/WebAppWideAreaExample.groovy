package brooklyn.demo

import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.OldJavaWebApp
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.policy.ResizerPolicy

public abstract class WebAppWideAreaExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebAppWideAreaExample)

    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]

    private static final String WAR_PATH = "src/main/resources/swf-booking-mvc.war"

    private DynamicFabric webFabric
    private DynamicGroup nginxEntities
    private GeoscalingDnsService geoDns
    
    protected abstract Closure getWebServerFactory();
    
    WebAppWideAreaExample(Map props=[:]) {
        super(props)
    }
    
    void init() {
        Closure webServerFactory = getWebServerFactory()
        
        Closure webClusterFactory = { Map flags, Entity owner ->
            NginxController nginxController = new NginxController(
                    domain:'brooklyn.geopaas.org',
                    port:8000,
                    portNumberSensor:OldJavaWebApp.HTTP_PORT)

            Map clusterFlags = new HashMap(flags) + [controller:nginxController, webServerFactory:webServerFactory]
            ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(clusterFlags, owner)
            
            ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
            policy.setMinSize(1)
            policy.setMaxSize(5)
            policy.setMetricLowerBound(10)
            policy.setMetricUpperBound(100)
            webCluster.cluster.addPolicy(policy)

            return webCluster
        }
        
        webFabric = new DynamicFabric(
            [
                displayName : 'web-cluster-fabric',
                displayNamePrefix : '',
                displayNameSuffix : ' web cluster',
                newEntity : webClusterFactory],
            this)
        webFabric.setConfig(OldJavaWebApp.WAR, WAR_PATH)
        webFabric.setConfig(Cluster.INITIAL_SIZE, 1)
        
        nginxEntities = new DynamicGroup([displayName: 'Web Fronts'], this, { Entity e -> (e instanceof NginxController) })
        geoDns = new GeoscalingDnsService(displayName: 'Geo-DNS',
            username: 'cloudsoft', password: 'cl0uds0ft', primaryDomainName: 'geopaas.org', smartSubdomainName: 'brooklyn',
            this)
        geoDns.setTargetEntityProvider(nginxEntities)
    }
}

public class JBossWideAreaExample extends WebAppWideAreaExample {
    public static void main(String[] argv) {
        List<Location> locations = Locations.getLocationsById(Arrays.asList(argv) ?: DEFAULT_LOCATIONS)

        JBossWideAreaExample app = new JBossWideAreaExample(displayName:'Brooklyn Wide-Area Seam Booking Example Application')
        app.init()
        
        BrooklynLauncher.manage(app)
        app.start(locations)
    }

    protected Closure getWebServerFactory() {
        return { Map properties, Entity cluster ->
            def server = new JBoss7Server(properties)
            server.setConfig(OldJavaWebApp.HTTP_PORT.configKey, 8080)
            return server;
        }

    }
}

public class TomcatWideAreaExample extends WebAppWideAreaExample {
    public static void main(String[] argv) {
        List<Location> locations = Locations.getLocationsById(Arrays.asList(argv) ?: DEFAULT_LOCATIONS)

        TomcatWideAreaExample app = new TomcatWideAreaExample(displayName:'Tomcat Wide-Area Example Application')
        app.init()
        
        BrooklynLauncher.manage(app)
        app.start(locations)
    }

    protected Closure getWebServerFactory() {
        return { Map properties, Entity cluster ->
            def server = new TomcatServer(properties)
            server.setConfig(OldJavaWebApp.HTTP_PORT.configKey, 8080)
            return server;
        }
    }
}
