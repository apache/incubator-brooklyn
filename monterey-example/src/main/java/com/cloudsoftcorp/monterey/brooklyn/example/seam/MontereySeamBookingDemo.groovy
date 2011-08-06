package com.cloudsoftcorp.monterey.brooklyn.example.seam

import static brooklyn.entity.group.Cluster.*
import static brooklyn.entity.basic.Attributes.*
import static brooklyn.entity.webapp.JavaWebApp.*
import static brooklyn.entity.webapp.jboss.JBoss6Server.*
import static brooklyn.entity.webapp.jboss.JBoss7Server.*
import static brooklyn.event.basic.DependentConfiguration.*
import static com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode.*
import static com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork.*
import static com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType.*

import java.util.List
import java.util.Map
import java.util.concurrent.atomic.AtomicInteger

import brooklyn.demo.Locations
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp;
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.policy.ResizerPolicy

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode
import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.util.web.client.CredentialsConfig

public class MontereySeamBookingDemo extends AbstractApplication {
    private static final UserCredentialsConfig MONTEREY_ADMIN_CREDENTIAL = new UserCredentialsConfig("myname", "mypass", "admin")
    
    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]
    
    // FIXME For localhost-mode
//    AtomicInteger nextPort = new AtomicInteger(9000)
//    AtomicInteger portIncrement = new AtomicInteger(920)
    
    public static void main(String[] argv) {
        List<Location> locations = loadLocations(argv)

        MontereySeamBookingDemo app = new MontereySeamBookingDemo(
                name : "brooklyn-wide-area-demo",
                displayName : "Brooklyn Wide-Area Seam Booking Demo Application"
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
    
    public MontereySeamBookingDemo(Map props=[:]) {
        super(props)

        montereyNetwork = new MontereyNetwork(
                name : "Seam Booking",
                appBundles : [ "src/main/resources/com.cloudsoftcorp.sample.booking.svc.api.jar",
		                       "src/main/resources/com.cloudsoftcorp.sample.booking.svc.impl_3.2.0.v20110502-351-10779.jar" ],
                appDescriptor : "src/main/resources/BookingAvailabilityApplication.conf",
                initialTopologyPerLocation : [ LPP:1, MR:1, M:1, TP:1, SPARE:1 ],
                webUsersCredential : [MONTEREY_ADMIN_CREDENTIAL], 
                webApiPort : 8090,
                
                // FIXME For local testing only...
//                managementNodeInstallDir : "/tmp/monterey/monterey-management-node",
//                networkNodeInstallDir : "/tmp/monterey/monterey-network-node",
//                maxConcurrentProvisioningsPerLocation : 1,
    
                this)

        Closure webServerFactory = { Map properties, Entity cluster ->
            def server = new JBoss7Server(properties)
            server.setConfig(HTTP_PORT.configKey, 8080)
            server.setConfig(MANAGEMENT_PORT.configKey, 8090)
//            server.setConfig(HTTP_PORT.configKey, nextPort.incrementAndGet())
//            server.setConfig(MANAGEMENT_PORT.configKey, nextPort.incrementAndGet())
//            server.setConfig(SUGGESTED_PORT_INCREMENT, portIncrement.addAndGet(100))
            server.setConfig(PROPERTY_FILES.subKey("MONTEREY_CONFIG"),
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
                    domain : "brooklyn.geopaas.org",
                    port : 8000,
                    portNumberSensor : HTTP_PORT)

            Map clusterFlags = [ controller : nginxController, webServerFactory : webServerFactory] << flags
            ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(clusterFlags, owner)
            
            ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
            policy.minSize = 1
            policy.maxSize = 5
            policy.metricLowerBound = 5
            policy.metricUpperBound = 30
            webCluster.cluster.addPolicy(policy)

            return webCluster
        }
        
        webFabric = new DynamicFabric(
                name : "web-cluster-fabric",
                displayName : "Fabric",
                displayNamePrefix : "",
                displayNameSuffix : " web cluster",
                newEntity : webClusterFactory,
                this)
        webFabric.setConfig(WAR, "src/main/resources/monterey-booking-as7.war")
//        webFabric.setConfig(WAR, "src/main/resources/monterey-booking-as6.war")
//        webFabric.setConfig(WAR, "src/main/resources/original-booking-as7.war")
//        webFabric.setConfig(WAR, "src/main/resources/original-booking-as6.war")
        webFabric.setConfig(INITIAL_SIZE, 1)
        
        nginxEntities = new DynamicGroup(
                displayName : "Web Fronts",
                this, { Entity e -> (e instanceof NginxController) })
        geoDns = new GeoscalingDnsService(
                displayName : "Geo-DNS",
                username: "cloudsoft",
                password: "cl0uds0ft",
                primaryDomainName: "geopaas.org",
                smartSubdomainName: "brooklyn",
                this)
        geoDns.setTargetEntityProvider(nginxEntities)
    }
}
	