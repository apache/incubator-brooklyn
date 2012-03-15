package brooklyn.demo.tomcat.todo;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation


class TomcatServerApp extends AbstractApplication {

    static BrooklynProperties sysProps = BrooklynProperties.Factory.newWithSystemAndEnvironment().addFromUrl("file:///~/brooklyn.properties");
    
    def tomcat = new TomcatServer(owner: this, httpPort: 8080, war: sysProps.getFirst("brooklyn.example.war", defaultIfNone: "/tmp/swf-booking-mvc.war"))

        
    public static void main(String... args) {
        TomcatServerApp demo = new TomcatServerApp(displayName : "tomcat server example")
        BrooklynLauncher.manage(demo)
        demo.start( [ new LocalhostMachineProvisioningLocation() ] )
    }

}
