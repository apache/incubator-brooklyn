package brooklyn.demo.legacy;

import static brooklyn.event.basic.DependentConfiguration.valueWhenAttributeReady;

import java.io.IOException;
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
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * Same as the {@link WebClusterDatabaseExample} but pure Java.
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath.
 * 
 * @deprecated in 0.5; see {@link brooklyn.demo.WebClusterDatabaseExample}
 */
@Deprecated
public class WebClusterDatabaseExampleAltJava extends AbstractApplication {
    private static final long serialVersionUID = -3549130575905836518L;
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleAltJava.class);
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

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

    public WebClusterDatabaseExampleAltJava() {
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
                .metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build());
    }    

    public static void main(String[] argv) throws IOException {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        WebClusterDatabaseExampleAltJava app = new WebClusterDatabaseExampleAltJava();
        app.setDisplayName("Brooklyn WebApp Cluster with Database example");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(app)
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
    
}
