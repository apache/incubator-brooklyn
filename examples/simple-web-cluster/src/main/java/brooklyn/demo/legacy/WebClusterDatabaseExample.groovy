package brooklyn.demo.legacy

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp
import static brooklyn.entity.webapp.WebAppServiceConstants.HTTP_PORT
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady
import static brooklyn.event.basic.DependentConfiguration.formatString

import java.util.List;

import groovy.transform.InheritConstructors

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.Lists;

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.entity.database.mysql.MySqlNodeImpl
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.ControlledDynamicWebAppClusterImpl
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.launcher.BrooklynServerDetails
import brooklyn.location.Location
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.util.CommandLineUtil

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * 
 * @deprecated in 0.5; see {@link brooklyn.demo.WebClusterDatabaseExample}
 */
@Deprecated
public class WebClusterDatabaseExample extends AbstractApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExample)
    
    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war"
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql"
    
    public static final String DB_TABLE = "visitors"
    public static final String DB_USERNAME = "brooklyn"
    public static final String DB_PASSWORD = "br00k11n"
    
    
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppClusterImpl(this, war: WAR_PATH);
    MySqlNode mysql = new MySqlNodeImpl(this, creationScriptUrl: DB_SETUP_SQL_URL);

    {
        web.configure(HTTP_PORT, "8080+").
            configure(javaSysProp("brooklyn.example.db.url"),
                formatString("jdbc:%s%s?user=%s\\&password=%s",
                    attributeWhenReady(mysql, MySqlNode.MYSQL_URL),
                    DB_TABLE, DB_USERNAME, DB_PASSWORD));

        web.cluster.addPolicy(AutoScalerPolicy.builder().
            metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE).
            sizeRange(1, 5).
            metricRange(10, 100).
            build());
    }

    public WebClusterDatabaseExample(Map properties){
          super(properties);
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(new WebClusterDatabaseExample(name:'Brooklyn WebApp Cluster with Database example'))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
    
}
