package brooklyn.demo.legacy;

import static brooklyn.event.basic.DependentConfiguration.valueWhenAttributeReady;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.database.mysql.MySqlNodeImpl;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.ControlledDynamicWebAppClusterImpl;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationRegistry;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;

import com.google.common.base.Function;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * Same as the {@link WebClusterDatabaseExample} but pure Java.
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath. 
 **/
public class WebClusterDatabaseExampleAltJava extends AbstractApplication {
    private static final long serialVersionUID = -3549130575905836518L;
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleAltJava.class);
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

    public static final String DEFAULT_LOCATION = "localhost";

    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";

    public static String makeJdbcUrl(String dbUrl) {
        //jdbc:mysql://192.168.1.2:3306/visitors?user=brooklyn&password=br00k11n
        return "jdbc:"+dbUrl+"visitors"+"?"+
                "user="+DB_USERNAME+"\\&"+
                "password="+DB_PASSWORD;
    }

    public WebClusterDatabaseExampleAltJava() { this(new LinkedHashMap()); }
    public WebClusterDatabaseExampleAltJava(Map props) {
        super(props);
    }
    
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppClusterImpl(this);
    MySqlNodeImpl mysql = new MySqlNodeImpl(this);

    {
        ((EntityLocal)web).setConfig(ControlledDynamicWebAppCluster.ROOT_WAR, WAR_PATH); 
        mysql.setConfig(MySqlNode.CREATION_SCRIPT_URL, DB_SETUP_SQL_URL);
        web.getFactory().setConfig(WebAppService.HTTP_PORT, "8080+"); 
        Map jvmSysProps = new LinkedHashMap();
        jvmSysProps.put("brooklyn.example.db.url", valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL, 
                new Function() {
                    @Override
                    public Object apply(Object input) {
                        return makeJdbcUrl(""+input);
                    }
                }));
        web.getFactory().setConfig(JBoss7Server.JAVA_SYSPROPS, jvmSysProps);

        web.getCluster().addPolicy(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build());
    }    

    public static void main(String[] argv) throws IOException {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        List<Location> locations = new LocationRegistry(properties).getLocationsById(
                !args.isEmpty() ? args : Collections.singletonList(DEFAULT_LOCATION));

        WebClusterDatabaseExampleAltJava app = new WebClusterDatabaseExampleAltJava();
        app.setDisplayName("Brooklyn WebApp Cluster with Database example");
            
        BrooklynLauncher.manage(app, port);
        app.start(locations);
        Entities.dumpInfo(app);
    }
    
}
