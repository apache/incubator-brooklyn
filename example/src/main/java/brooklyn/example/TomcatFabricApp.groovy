package brooklyn.example

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation


class TomcatFabricApp extends AbstractApplication {
    
    // FIXME Do we need to pass the flags/owner into ControlledDynamicWebAppCluster?
    
    public static void main(String[] argv) {
        TomcatFabricApp demo = new TomcatFabricApp(displayName : "tomcat example")
        demo.init()
        BrooklynLauncher.manage(demo)
        
        Location loc = new LocalhostMachineProvisioningLocation(count: 3)
        Location loc2 = new LocalhostMachineProvisioningLocation(count: 3)
        demo.start([loc, loc2])
    }

    public void init() {
        Closure webClusterFactory = { Map flags, Entity owner ->
            return new DynamicWebAppCluster(
                    newEntity: { properties -> new TomcatServer(properties) },
                    owner:this)
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
