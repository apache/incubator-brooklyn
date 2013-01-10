package brooklyn.demo

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp
import static brooklyn.entity.webapp.WebAppServiceConstants.HTTP_PORT
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady
import static brooklyn.event.basic.DependentConfiguration.formatString
import groovy.transform.InheritConstructors

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.entity.database.mysql.MySqlNodeImpl
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.launcher.BrooklynServerDetails
import brooklyn.location.Location
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.util.CommandLineUtil

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 **/
@InheritConstructors
public class WebClusterDatabaseExample extends AbstractApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExample)
    
    public static final String DEFAULT_LOCATION = "localhost"

    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war"
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql"
    
    public static final String DB_TABLE = "visitors"
    public static final String DB_USERNAME = "brooklyn"
    public static final String DB_PASSWORD = "br00k11n"
    
    
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this, war: WAR_PATH);
    MySqlNode mysql = new MySqlNodeImpl(this, creationScriptUrl: DB_SETUP_SQL_URL);

    {
        web.configure(HTTP_PORT, "8080+").
            configure(javaSysProp("brooklyn.example.db.url"),
                formatString("jdbc:%s%s?user=%s\\&password=%s",
                    attributeWhenReady(mysql, MySqlNode.MYSQL_URL),
                    DB_TABLE, DB_USERNAME, DB_PASSWORD));

        web.cluster.addPolicy(AutoScalerPolicy.builder().
            metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
            sizeRange(1, 5).
            metricRange(10, 100).
            build());
    }

    public static void main(String[] argv) {
        WebClusterDatabaseExample app = new WebClusterDatabaseExample(name:'Brooklyn WebApp Cluster with Database example')
        
        ArrayList args = new ArrayList(Arrays.asList(argv));
        BrooklynServerDetails server = BrooklynLauncher.newLauncher().
                webconsolePort( CommandLineUtil.getCommandLineOption(args, "--port", "8081+") ).
                managing(app).
                launch();

        List<Location> locations = server.getManagementContext().getLocationRegistry().resolve(args ?: [DEFAULT_LOCATION])
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}
