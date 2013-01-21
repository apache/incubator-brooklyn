package brooklyn.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Launches a clustered and load-balanced set of web servers.
 * Demonstrates syntax, so many of the options used here are the defaults.
 * (So the class could be much simpler, as in WebClusterExampleAlt.)
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath. 
 **/
public class WebClusterExample extends ApplicationBuilder {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExample.class);
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

    public static final String DEFAULT_LOCATION = "localhost";

    public static final String WAR_PATH = "classpath://hello-world-webapp.war";

    private NginxController nginxController;
    private ControlledDynamicWebAppCluster web;
    
    protected void doBuild() {
        nginxController = createChild(BasicEntitySpec.newInstance(NginxController.class)
                //.configure("domain", "webclusterexample.brooklyn.local")
                .configure("port", "8000+"));
          
        web = createChild(ControlledDynamicWebAppCluster.Spec.newInstance()
                .displayName("WebApp cluster")
                .controller(nginxController)
                .initialSize(1)
                .memberSpec(BasicEntitySpec.newInstance(JBoss7Server.class)
                        .configure("httpPort", "8080+")
                        .configure("war", WAR_PATH)));
        
        web.getCluster().addPolicy(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build());
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        // TODO Want to parse, to handle multiple locations
        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        BasicApplication app = (BasicApplication) new WebClusterExample()
                .appDisplayName("Brooklyn WebApp Cluster example")
                .manage(server.getManagementContext());

        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
    }
}
