package brooklyn.example.cloudfoundry;

import java.util.List;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.launcher.BrooklynLauncherCli;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class MovableCloudFoundryClusterExample extends ApplicationBuilder {

    public static final String DEFAULT_LOCATION = "cloudfoundry";
    public static final String WAR_FILE_URL = "classpath://hello-world-webapp.war";

    @Override
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(MovableElasticWebAppCluster.class)
                .configure("war", WAR_FILE_URL));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncherCli launcher = BrooklynLauncherCli.newInstance()
                .application(new MovableCloudFoundryClusterExample().appDisplayName("Movable Web Cluster"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
