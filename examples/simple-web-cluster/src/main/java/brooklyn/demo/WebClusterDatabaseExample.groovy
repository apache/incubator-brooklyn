package brooklyn.demo

import static brooklyn.event.basic.DependentConfiguration.valueWhenAttributeReady
import static brooklyn.entity.java.JavaEntityMethods.javaSysProp
import groovy.transform.InheritConstructors

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.entity.java.UsesJava
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
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
    
    public static final String DB_USERNAME = "brooklyn"
    public static final String DB_PASSWORD = "br00k11n"
    
    public static String makeJdbcUrl(String dbUrl) {
        //jdbc:mysql://192.168.1.2:3306/visitors?user=brooklyn&password=br00k11n
        return "jdbc:"+dbUrl+"visitors"+"?"+"user="+DB_USERNAME+"\\&"+"password="+DB_PASSWORD;
    }

    
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this, war: WAR_PATH);
    MySqlNode mysql = new MySqlNode(this, creationScriptUrl: DB_SETUP_SQL_URL);

    {
        web.factory.
            configure(httpPort: "8080+").
            configure(javaSysProp("brooklyn.example.db.url"),
                valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL, this.&makeJdbcUrl));

        web.cluster.addPolicy(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build());
    }

    public static void main(String[] argv) {
        WebClusterDatabaseExample app = new WebClusterDatabaseExample(name:'Brooklyn WebApp Cluster with Database example')
        
        ArrayList args = new ArrayList(Arrays.asList(argv));
        BrooklynLauncher.newLauncher().
                webconsolePort( CommandLineUtil.getCommandLineOption(args, "--port", "8081+") ).
                managing(app).
                launch();

        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}
