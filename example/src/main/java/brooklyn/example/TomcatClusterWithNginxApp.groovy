package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.group.Cluster
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation


class TomcatClusterWithNginxApp extends AbstractApplication {
    
    public static void main(String[] argv) {
        TomcatClusterWithNginxApp demo = new TomcatClusterWithNginxApp(displayName : "tomcat cluster with nginx example")
        demo.init()
        BrooklynLauncher.manage(demo)
        
        Location loc = new LocalhostMachineProvisioningLocation(count: 3)
        demo.start([loc])
    }

    public void init() {
        NginxController nginxController = new NginxController(
                domain : "brooklyn.geopaas.org",
                port : 8000,
                portNumberSensor : Attributes.HTTP_PORT)
    
        ControlledDynamicWebAppCluster cluster = new ControlledDynamicWebAppCluster(
                controller : nginxController,
                webServerFactory : { properties -> new TomcatServer(properties) },
                owner : this)
        
        cluster.setConfig(JavaWebApp.WAR, "/path/to/booking-mvc.war")
        cluster.setConfig(Cluster.INITIAL_SIZE, 2)
    }
}
