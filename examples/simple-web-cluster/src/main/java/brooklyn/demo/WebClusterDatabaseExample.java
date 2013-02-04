package brooklyn.demo;

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.enricher.basic.SensorTransformingEnricher;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 **/
public class WebClusterDatabaseExample extends ApplicationBuilder {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExample.class);
    
    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    public static final BasicAttributeSensor<Integer> APPSERVERS_COUNT = new BasicAttributeSensor<Integer>(Integer.class, 
            "appservers.count", "Number of app servers deployed");

    protected void doBuild() {
        MySqlNode mysql = createChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL));
        
        ControlledDynamicWebAppCluster web = createChild(BasicEntitySpec.newInstance(ControlledDynamicWebAppCluster.class)
                .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                .configure(javaSysProp("brooklyn.example.db.url"), 
                        formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                attributeWhenReady(mysql, MySqlNode.MYSQL_URL), 
                                DB_TABLE, DB_USERNAME, DB_PASSWORD)));
        
        // simple scaling policy
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(1, 5).
                build());

        // expose some KPI's
        getApplication().addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(web,  
                WebAppServiceConstants.ROOT_URL,
                DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW));
        getApplication().addEnricher(new SensorTransformingEnricher<Integer,Integer>(web, 
                DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT, Functions.<Integer>identity()));
    }

    public static void main(String[] argv) {
        // TODO want to crate a simpler way to do the boilerplate psv main; suggestion here: 
//        BrooklynApplicationCli cli = BrooklynApplicationCli.newInstance().
//            application(new WebClusterDatabaseExample()
//                .appDisplayName("Brooklyn WebApp Cluster with Database example")).
//            defaultLocations("localhost").
//            start();
//        
//        Entities.dumpInfo(cli.getApplication());
        
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        StartableApplication app = new WebClusterDatabaseExample()
                .appDisplayName("Brooklyn WebApp Cluster with Database example")
                .manage(server.getManagementContext());
        
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
    }
}
