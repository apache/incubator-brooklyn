package brooklyn.example.cloudfoundry;

import groovy.transform.InheritConstructors
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.util.CommandLineUtil

@InheritConstructors
public class MovableCloudFoundryClusterExample extends AbstractApplication implements MovableEntityTrait {

    public static final String DEFAULT_LOCATION = "cloudfoundry"
    public static final String WAR_FILE_URL = "classpath://hello-world-webapp.war";

    MovableElasticWebAppCluster websvc = new MovableElasticWebAppCluster(this, war: WAR_FILE_URL);
    
    @Override
    public String move(String location) {
        websvc.move(location);
    }
    
    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])

        MovableCloudFoundryClusterExample app = new MovableCloudFoundryClusterExample(name:'Movable Web Cluster')
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
}
