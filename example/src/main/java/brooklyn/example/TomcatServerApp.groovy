package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

class TomcatServerApp extends AbstractApplication {
    
    public static void main(String[] argv) {
        TomcatServerApp demo = new TomcatServerApp(displayName : "tomcat server example")
        demo.init()
        BrooklynLauncher.manage(demo)
        
        Location loc = new LocalhostMachineProvisioningLocation(count: 1)
        demo.start([loc])
    }

    public void init() {
        def tomcat = new TomcatServer(owner:this)
        tomcat.setConfig(TomcatServer.HTTP_PORT.configKey, 8080)
        tomcat.setConfig(TomcatServer.WAR, "/path/to/booking-mvc.war")
    }
}
