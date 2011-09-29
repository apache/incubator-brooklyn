package brooklyn.example

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation


class TomcatFabricGeoApp extends AbstractApplication {
    
    // FIXME Do we need to pass the flags/owner into ControlledDynamicWebAppCluster?
    
    public static void main(String[] argv) {
        TomcatFabricGeoApp demo = new TomcatFabricGeoApp(displayName : "tomcat example")
        demo.init()
        BrooklynLauncher.manage(demo)
        
        Location loc = new LocalhostMachineProvisioningLocation(count: 4)
        demo.start([loc])
    }

    public void init() {
        Closure webClusterFactory = { Map flags, Entity owner ->
            NginxController nginxController = new NginxController(
                    domain : "brooklyn.geopaas.org",
                    port : 8000,
                    portNumberSensor : Attributes.HTTP_PORT)
        
            ControlledDynamicWebAppCluster cluster = new ControlledDynamicWebAppCluster(
                    controller : nginxController,
                    webServerFactory : { properties -> new TomcatServer(properties) },
                    owner : this)
        }

        DynamicFabric fabric = new DynamicFabric(
                displayName : "WebFabric",
                displayNamePrefix : "",
                displayNameSuffix : " web cluster",
                newEntity : webClusterFactory,
                this)
        
        fabric.setConfig(JavaWebApp.WAR, "/path/to/booking-mvc.war")
        fabric.setConfig(Cluster.INITIAL_SIZE, 2)
    }
}
