package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation


class TomcatClusterApp extends AbstractApplication {
    
    public static void main(String[] argv) {
        TomcatClusterApp demo = new TomcatClusterApp(displayName : "tomcat cluster example")
        demo.init()
        BrooklynLauncher.manage(demo)
        
        Location loc = new LocalhostMachineProvisioningLocation(count: 4)
        demo.start([loc])
    }

    public void init() {
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
                initialSize: 1,
                newEntity: { properties -> new TomcatServer(properties) },
                owner:this)
        cluster.setConfig(TomcatServer.HTTP_PORT.configKey, 8080)
        cluster.setConfig(JavaWebApp.WAR, "/path/to/booking-mvc.war")
    }
}
