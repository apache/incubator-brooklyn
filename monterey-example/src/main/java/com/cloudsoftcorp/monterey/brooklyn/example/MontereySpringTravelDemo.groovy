package com.cloudsoftcorp.monterey.brooklyn.example

import static com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType.LPP
import static com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType.M
import static com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType.MR
import static com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType.SPARE
import static com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType.TP

import java.net.URL
import java.util.List
import java.util.Map

import brooklyn.demo.Locations
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
import brooklyn.event.basic.DependentConfiguration
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.policy.ResizerPolicy

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyContainerNode
import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode
import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig

public class MontereySpringTravelDemo extends AbstractApplication {
 
    private static final File SPRING_TRAVEL_WAR_FILE = new File("src/main/resources/booking-mvc.war")
    private static final List<URL> MONTEREY_APP_BUNDLE_URLS = [
            new File("src/main/resources/com.cloudsoftcorp.sample.booking.svc.api.jar").toURI().toURL(),
            new File("src/main/resources/com.cloudsoftcorp.sample.booking.svc.impl_3.2.0.v20110502-351-10779.jar").toURI().toURL()]
    
    private static final URL MONTEREY_APP_DESCRIPTOR_URL = new File("src/main/resources/BookingAvailabilityApplication.conf").toURI().toURL()
    private static final UserCredentialsConfig MONTEREY_ADMIN_CREDENTIAL = new UserCredentialsConfig("myname", "mypass", "admin");
    private static final Map MONTEREY_TOPOLOGY_PER_LOCATION = [(LPP):1,(MR):1,(M):1,(TP):1,(SPARE):1]
    
    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]
    
    public static void main(String[] argv) {
        List<Location> locations = loadLocations(argv)

        MontereySpringTravelDemo app = new MontereySpringTravelDemo(name:'brooklyn-wide-area-demo',
                displayName:'Brooklyn Wide-Area Spring Travel Demo Application')
        
        BrooklynLauncher.manage(app)
        app.start(locations)
    }

    private static List<Location> loadLocations(String[] argv) {
        // Parse arguments for location ids and resolve each into a location
        List<String> ids = argv.length == 0 ? DEFAULT_LOCATIONS : Arrays.asList(argv)
        List<Location> locations = Locations.getLocationsById(ids)
        println "Starting in locations: "+ids
        return locations
    }

    final MontereyNetwork montereyNetwork
    final DynamicFabric webFabric
    final DynamicGroup nginxEntities
    final GeoscalingDnsService geoDns
    
    public MontereySpringTravelDemo(Map props=[:]) {
        super(props)

        montereyNetwork = new MontereyNetwork(owner:this)
        montereyNetwork.name = "Spring Travel"
        montereyNetwork.setConfig(MontereyNetwork.APP_BUNDLES, MONTEREY_APP_BUNDLE_URLS)
        montereyNetwork.setConfig(MontereyNetwork.APP_DESCRIPTOR_URL, MONTEREY_APP_DESCRIPTOR_URL)
        montereyNetwork.setConfig(MontereyNetwork.APP_DESCRIPTOR_URL, MONTEREY_APP_DESCRIPTOR_URL)
        montereyNetwork.setConfig(MontereyManagementNode.WEB_USERS_CREDENTIAL, [MONTEREY_ADMIN_CREDENTIAL])
        montereyNetwork.setConfig(MontereyNetwork.INITIAL_TOPOLOGY_PER_LOCATION, [(LPP):1,(MR):1,(M):1,(TP):1,(SPARE):1])
        
        // FIXME For local testing only...
//        montereyNetwork.setConfig(MontereyManagementNode.MANAGEMENT_NODE_INSTALL_DIR, "/Users/aled/monterey-management-node")
//        montereyNetwork.setConfig(MontereyContainerNode.NETWORK_NODE_INSTALL_DIR, "/Users/aled/monterey-network-node-copy1")
//        montereyNetwork.setConfig(MontereyNetwork.MAX_CONCURRENT_PROVISIONINGS_PER_LOCATION, 1)
//        montereyNetwork.setConfig(MontereyManagementNode.WEB_API_PORT, 8090)
        
        //mn.policy << new MontereyLatencyOptimisationPolicy()

        Closure webServerFactory = { Map properties, Entity cluster ->
            def server = new TomcatServer(properties)
            server.setConfig(JavaWebApp.HTTP_PORT.configKey, 8080)
            server.setConfig(TomcatServer.PROPERTY_FILES.subKey("MONTEREY_PROPERTIES"),
                    [
                        montereyManagementUrl:DependentConfiguration.attributePostProcessedWhenReady(montereyNetwork, MontereyNetwork.MANAGEMENT_URL, {it}, {it.toString()}),
                        montereyUser:DependentConfiguration.attributePostProcessedWhenReady(montereyNetwork, MontereyNetwork.CLIENT_CREDENTIAL, {it}, {it.username}),
                        montereyPassword:DependentConfiguration.attributePostProcessedWhenReady(montereyNetwork, MontereyNetwork.CLIENT_CREDENTIAL, {it}, {it.password}),
                        montereyLocation:cluster.locations.first().findLocationProperty("iso3166").first()])
            
            // Or could have been...?
            // server.setConfig(PROPERTIES_FILE_ON_CLASSPATH.subKey("monterey.properties"), [...])
            // server.setConfig(SYSTEM_PROPERTIES.subKey("montereyManagementUrl"), montereyManagementUrl=DependentConfiguration.attributeWhenReady(montereyNetwork, MontereyNetwork.MANAGEMENT_URL))
            
            return server;
        }
        
        Closure webClusterFactory = { Map flags, Entity owner ->
            NginxController nginxController = new NginxController(
                    domain:'brooklyn.geopaas.org',
                    port:8000,
                    portNumberSensor:JavaWebApp.HTTP_PORT)

            Map clusterFlags = flags.clone()
            clusterFlags.controller = nginxController
            clusterFlags.webServerFactory = webServerFactory
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
        webFabric.setConfig(JavaWebApp.WAR, SPRING_TRAVEL_WAR_FILE)
        webFabric.setConfig(Cluster.INITIAL_SIZE, 1)
        
        nginxEntities = new DynamicGroup([displayName: 'Web Fronts'], this, { Entity e -> (e instanceof NginxController) })
        geoDns = new GeoscalingDnsService(displayName: 'Geo-DNS',
            username: 'cloudsoft', password: 'cl0uds0ft', primaryDomainName: 'geopaas.org', smartSubdomainName: 'brooklyn',
            this)
        geoDns.setTargetEntityProvider(nginxEntities)
    }
}
	