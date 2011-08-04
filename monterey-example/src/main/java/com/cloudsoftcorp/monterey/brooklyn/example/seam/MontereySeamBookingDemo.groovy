package com.cloudsoftcorp.monterey.brooklyn.example.seam

import static brooklyn.entity.webapp.JavaWebApp.*
import static brooklyn.entity.group.Cluster.*
import static brooklyn.event.basic.DependentConfiguration.*
import static com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode.*
import static com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork.*
import static com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType.*

import java.util.List
import java.util.Map

import brooklyn.demo.Locations
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.policy.ResizerPolicy

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode
import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.util.web.client.CredentialsConfig

public class MontereSeamBookingDemo extends AbstractApplication {
    private static final UserCredentialsConfig MONTEREY_ADMIN_CREDENTIAL = new UserCredentialsConfig("myname", "mypass", "admin")
    
    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]
    
    public static void main(String[] argv) {
        List<Location> locations = loadLocations(argv)

        MontereSeamBookingDemo app = new MontereSeamBookingDemo(
                name : 'brooklyn-wide-area-demo',
                displayName : 'Brooklyn Wide-Area Seam Booking Demo Application'
            )
        
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
    
    public MontereSeamBookingDemo(Map props=[:]) {
        super(props)

        montereyNetwork = new MontereyNetwork(owner:this)
        montereyNetwork.name = "Seam Booking"
        montereyNetwork.setConfig APP_BUNDLES,
                [ "src/main/resources/com.cloudsoftcorp.sample.booking.svc.api.jar",
                  "src/main/resources/com.cloudsoftcorp.sample.booking.svc.impl_3.2.0.v20110502-351-10779.jar" ]
        montereyNetwork.setConfig APP_DESCRIPTOR_URL, "src/main/resources/BookingAvailabilityApplication.conf"
        montereyNetwork.setConfig WEB_USERS_CREDENTIAL, [ MONTEREY_ADMIN_CREDENTIAL ]
        montereyNetwork.setConfig INITIAL_TOPOLOGY_PER_LOCATION,  [ LPP:1, MR:1, M:1, TP:1, SPARE:1 ]
        //montereyNetwork.policy << new MontereyLatencyOptimisationPolicy()

        Closure webServerFactory = { Map properties, Entity cluster ->
            def server = new TomcatServer(properties)
            server.setConfig(HTTP_PORT.configKey, 8080)
            server.setConfig(TomcatServer.PROPERTY_FILES.subKey("MONTEREY_PROPERTIES"),
                    [
                        montereyManagementUrl : attributeWhenReady(montereyNetwork, MANAGEMENT_URL),
                        montereyUser : attributePostProcessedWhenReady(montereyNetwork, CLIENT_CREDENTIAL, { CredentialsConfig config -> config.username }),
                        montereyPassword : attributePostProcessedWhenReady(montereyNetwork, CLIENT_CREDENTIAL, { CredentialsConfig config -> config.password }),
                        montereyLocation : cluster.locations.first().findLocationProperty("iso3166").first()
                    ])
            return server;
        }
        
        Closure webClusterFactory = { Map flags, Entity owner ->
            NginxController nginxController = new NginxController(
                    domain:'brooklyn.geopaas.org',
                    port:8000,
                    portNumberSensor:HTTP_PORT)

            Map clusterFlags = [ controller : nginxController, webServerFactory : webServerFactory] << flags
            ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(clusterFlags, owner)
            
            ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
            policy.minSize = 1
            policy.maxSize = 5
            policy.metricLowerBound = 10
            policy.metricUpperBound = 100
            webCluster.cluster.addPolicy(policy)

            return webCluster
        }
        
        webFabric = new DynamicFabric(
                name : 'web-cluster-fabric',
                displayName : 'Fabric',
                displayNamePrefix : '',
                displayNameSuffix : ' web cluster',
                newEntity : webClusterFactory,
                this)
        webFabric.setConfig(WAR, "src/main/resources/seam-booking.war")
        webFabric.setConfig(INITIAL_SIZE, 1)
        
        nginxEntities = new DynamicGroup(
                displayName : 'Web Fronts',
                this, { Entity e -> (e instanceof NginxController) })
        geoDns = new GeoscalingDnsService(
                displayName : 'Geo-DNS',
                username: 'cloudsoft',
                password: 'cl0uds0ft',
                primaryDomainName: 'geopaas.org',
                smartSubdomainName: 'brooklyn',
                this)
        geoDns.setTargetEntityProvider(nginxEntities)
    }
}
	