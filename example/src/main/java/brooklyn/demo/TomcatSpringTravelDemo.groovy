package brooklyn.demo

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
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.policy.ResizerPolicy
import brooklyn.util.IdGenerator;

public class TomcatSpringTravelDemo extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(TomcatSpringTravelDemo)

    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]

    private static final String WAR_PATH = "src/main/resources/swf-booking-mvc.war"

    public static void main(String[] argv) {
        List<String> ids = argv.length == 0 ? DEFAULT_LOCATIONS : Arrays.asList(argv)
        println "Starting in locations: "+ids
        List<Location> locations = Locations.getLocationsById(ids)

        TomcatSpringTravelDemo app = new TomcatSpringTravelDemo(name:'brooklyn-tomcat-wide-area-demo',
                displayName:'Brooklyn Tomcat Wide-Area Spring Travel Demo Application')

        BrooklynLauncher.manage(app)
        app.start(locations)
    }
    
    final DynamicFabric webFabric
    final DynamicGroup nginxEntities
    final GeoscalingDnsService geoDns
    
    TomcatSpringTravelDemo(Map props=[:]) {
        super(props)
        
        Closure webServerFactory = { Map properties, Entity cluster ->
            def server = new TomcatServer(properties)
            server.setConfig(JavaWebApp.HTTP_PORT.configKey, 8080)
            return server;
        }
        
        Closure webClusterFactory = { Map flags, Entity owner ->
            NginxController nginxController = new NginxController(
                    domain:'brooklyn.geopaas.org',
                    port:8000,
                    portNumberSensor:JavaWebApp.HTTP_PORT)

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
                name : 'web-cluster-fabric',
                displayName : 'Fabric',
                displayNamePrefix : '',
                displayNameSuffix : ' web cluster',
                newEntity : webClusterFactory],
            this)
        webFabric.setConfig(JavaWebApp.WAR, WAR_PATH)
        webFabric.setConfig(Cluster.INITIAL_SIZE, 1)
        
        nginxEntities = new DynamicGroup([displayName: 'Web Fronts'], this, { Entity e -> (e instanceof NginxController) })
        String randomSubdomain = 'brooklyn-'+IdGenerator.makeRandomId(8)
        geoDns = new GeoscalingDnsService(displayName: 'Geo-DNS',
            username: 'cloudsoft', password: 'cl0uds0ft', primaryDomainName: 'geopaas.org', smartSubdomainName: randomSubdomain,
            this)
        geoDns.setTargetEntityProvider(nginxEntities)
    }
}
