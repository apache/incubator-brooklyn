package brooklyn.example.cloudfoundry;

import java.util.List;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class MovableCloudFoundryClusterExample extends AbstractApplication {

    public static final String DEFAULT_LOCATION = "cloudfoundry";
    public static final String WAR_FILE_URL = "classpath://hello-world-webapp.war";

    @Override
    public void init() {
        addChild(EntitySpecs.spec(MovableElasticWebAppCluster.class)
                .configure("war", WAR_FILE_URL));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpecs.appSpec(MovableCloudFoundryClusterExample.class).displayName("Movable Web Cluster"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
