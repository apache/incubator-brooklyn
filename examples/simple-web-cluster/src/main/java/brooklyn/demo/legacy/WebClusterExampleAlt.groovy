package brooklyn.demo.legacy

import groovy.transform.InheritConstructors

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.ControlledDynamicWebAppClusterImpl
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.util.CommandLineUtil

import com.google.common.collect.Lists

/**
 * Launches a clustered and load-balanced set of web servers.
 * (Simplified variant of WebClusterExample.)
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath.
 * 
 * @deprecated in 0.5; see {@link brooklyn.demo.WebClusterExample}
 */
@Deprecated
@InheritConstructors
public class WebClusterExampleAlt extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExampleAlt)
    
    public static final String WAR_PATH = "classpath://hello-world-webapp.war"
    
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppClusterImpl(this, war: WAR_PATH);
    {
        web.addPolicy(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build());
    }
    

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");
        
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(new WebClusterExampleAlt(name:'Brooklyn WebApp Cluster example'))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
