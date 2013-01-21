package brooklyn.demo.legacy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.entity.database.mysql.MySqlNodeImpl
import brooklyn.entity.proxy.nginx.NginxControllerImpl
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.ControlledDynamicWebAppClusterImpl
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerImpl
import brooklyn.event.basic.DependentConfiguration
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.util.CommandLineUtil

/**
 * Shows some alternative syntaxes compared with WebClusterDatabaseExample.
 * (Inline SQL; different ports (nginx on 8080 now); policy added programmatically.
 * <p> 
 * Run with:
 *   java -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * <p>
 * This jar or classes dir, and brooklyn-all jar, on classpath. 
 **/
public class WebClusterDatabaseExampleAlt extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleAlt)
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final String DEFAULT_LOCATION = "localhost"

    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war"
    
    public static final String DB_USERNAME = "brooklyn"
    public static final String DB_PASSWORD = "br00k11n"
    
    public static final String DB_SETUP_SQL = """
create database visitors;
use visitors;
create user '${DB_USERNAME}' identified by '${DB_PASSWORD}';
grant usage on *.* to '${DB_USERNAME}'@'%' identified by '${DB_PASSWORD}';
# ''@localhost is sometimes set up, overriding brooklyn@'%', so do a second explicit grant
grant usage on *.* to '${DB_USERNAME}'@'localhost' identified by '${DB_PASSWORD}';
grant all privileges on visitors.* to '${DB_USERNAME}'@'%';
flush privileges;

CREATE TABLE MESSAGES (
        id INT NOT NULL AUTO_INCREMENT,
        NAME VARCHAR(30) NOT NULL,
        MESSAGE VARCHAR(400) NOT NULL,
        PRIMARY KEY (ID)
    );

INSERT INTO MESSAGES values (default, 'Isaac Asimov', 'I grew up in Brooklyn' );
""";

    public WebClusterDatabaseExampleAlt(Map props=[:]) {
        super(props)
        setConfig(JavaWebAppService.ROOT_WAR, WAR_PATH)
    }
    
    MySqlNode mysql = new MySqlNodeImpl(this, creationScriptContents: DB_SETUP_SQL);

    protected JavaWebAppService newWebServer(Map flags, Entity cluster) {
        JBoss7Server jb7 = new JBoss7ServerImpl(flags).configure(httpPort: "8000+");
        jb7.setConfig(JBoss7Server.JAVA_SYSPROPS, ["brooklyn.example.db.url": 
                //"jdbc:mysql://localhost/visitors?user=brooklyn&password=br00k11n"
                DependentConfiguration.valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL, 
                    { "jdbc:"+it+"visitors?user=${DB_USERNAME}\\&password=${DB_PASSWORD}" }) ]);
        jb7.setParent(cluster);
        return jb7;
    }

    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppClusterImpl(this,
        controller: new NginxControllerImpl(port: 8080),
        factory: this.&newWebServer )
    
    AutoScalerPolicy policy = AutoScalerPolicy.builder()
            .metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
            .sizeRange(1, 5)
            .metricRange(10, 100)
            .build();
    

    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])

        WebClusterDatabaseExampleAlt app = new WebClusterDatabaseExampleAlt(name:'Brooklyn WebApp Cluster with Database example')
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
        
        app.web.cluster.addPolicy(app.policy)
    }
    
}
