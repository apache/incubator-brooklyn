package brooklyn.demo;

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
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
 * Shows some alternative syntaxes compared with WebClusterDatabaseExample.
 * (Inline SQL; different ports (nginx on 8080 now); policy added programmatically.
 * <p> 
 * Run with:
 *   java -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * <p>
 * This jar or classes dir, and brooklyn-all jar, on classpath. 
 **/
public class WebClusterDatabaseExampleAlt extends ApplicationBuilder {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleAlt.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    public static final String DB_SETUP_SQL = 
            "create database visitors;"+"\n"+
            "use visitors;"+"\n"+
            "create user '"+DB_USERNAME+"' identified by '"+DB_PASSWORD+"';"+"\n"+
            "grant usage on *.* to '"+DB_USERNAME+"'@'%' identified by '"+DB_PASSWORD+"';"+"\n"+
            "# ''@localhost is sometimes set up, overriding brooklyn@'%', so do a second explicit grant"+"\n"+
            "grant usage on *.* to '"+DB_USERNAME+"'@'localhost' identified by '"+DB_PASSWORD+"';"+"\n"+
            "grant all privileges on visitors.* to '"+DB_USERNAME+"'@'%';"+"\n"+
            "flush privileges;"+"\n"+
            "\n"+
            "CREATE TABLE MESSAGES ("+"\n"+
            "\t"+"\t"+"id INT NOT NULL AUTO_INCREMENT,"+"\n"+
            "\t"+"\t"+"NAME VARCHAR(30) NOT NULL,"+"\n"+
            "\t"+"\t"+"MESSAGE VARCHAR(400) NOT NULL,"+"\n"+
            "\t"+"\t"+"PRIMARY KEY (ID)"+"\n"+
            "\t"+");"+"\n"+
            "\n"+
            "INSERT INTO MESSAGES values (default, 'Isaac Asimov', 'I grew up in Brooklyn' );"+"\n";

    protected void doBuild() {
        MySqlNode mysql = createChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptContents", DB_SETUP_SQL));
        
        ControlledDynamicWebAppCluster web = createChild(BasicEntitySpec.newInstance(ControlledDynamicWebAppCluster.class)
                .configure(JBoss7Server.ROOT_WAR, WAR_PATH)
                .configure(javaSysProp("brooklyn.example.db.url"), 
                        formatString("jdbc:%s%s?user=%s\\&password=%s", attributeWhenReady(mysql, MySqlNode.MYSQL_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                .configure("memberSpec", BasicEntitySpec.newInstance(JBoss7Server.class)
                        .configure("httpPort", "8080+")));

        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                        metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                        sizeRange(1, 5).
                        metricRange(10, 100).
                        build());
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        StartableApplication app = new WebClusterDatabaseExampleAlt()
                .appDisplayName("Brooklyn WebApp Cluster with Database example")
                .manage(server.getManagementContext());
        
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
    }
}
