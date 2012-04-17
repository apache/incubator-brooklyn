package brooklyn.demo

import static brooklyn.event.basic.DependentConfiguration.valueWhenAttributeReady

import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.policy.ResizerPolicy
import brooklyn.util.CommandLineUtil

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath. 
 **/
public class WebClusterDatabaseExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExample)
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final String DEFAULT_LOCATION = "localhost"

    public static final String WAR_PATH = "classpath://hello-world-webapp.war"
    
    public static final String DB_USERNAME = "brooklyn"
    public static final String DB_PASSWORD = "br00k11n"
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql"
    
    public static String makeJdbcUrl(String dbUrl) {
        //jdbc:mysql://192.168.1.2:3306/visitors?user=brooklyn&password=br00k11n
        "jdbc:"+dbUrl+"visitors"+"?"+
            "user="+DB_USERNAME+"\\&"+
            "password="+DB_PASSWORD
    }

    public WebClusterDatabaseExample(Map props=[:]) {
        super(props)
    }
    
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this, war: WAR_PATH);
    MySqlNode mysql = new MySqlNode(this, creationScriptUrl: DB_SETUP_SQL_URL);

    {
        web.factory.configure(
            httpPort: "8080+", 
            (JBoss7Server.JAVA_OPTIONS):
                ["brooklyn.example.db.url": valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL, this.&makeJdbcUrl)]);

        web.cluster.addPolicy(new
            ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                setSizeRange(1, 5).
                setMetricRange(10, 100));
    }    

    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])

        WebClusterDatabaseExample app = new WebClusterDatabaseExample(name:'Brooklyn WebApp Cluster with Database example')
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}
