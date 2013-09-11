package brooklyn.demo;

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.HttpLatencyDetector;
import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.enricher.basic.SensorTransformingEnricher;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 **/
public class WebClusterDatabaseExample extends AbstractApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExample.class);
    
    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    public static final AttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor( 
            "appservers.count", "Number of app servers deployed");

    @Override
    public void init() {
        MySqlNode mysql = addChild(EntitySpec.create(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL));
        
        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                .configure(javaSysProp("brooklyn.example.db.url"), 
                        formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                attributeWhenReady(mysql, MySqlNode.DB_URL), 
                                DB_TABLE, DB_USERNAME, DB_PASSWORD)) );

        web.addEnricher(HttpLatencyDetector.builder().
                url(ControlledDynamicWebAppCluster.ROOT_URL).
                rollup(10, TimeUnit.SECONDS).
                build());

        // simple scaling policy
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(1, 5).
                build());

        // expose some KPI's
        addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(web,  
                WebAppServiceConstants.ROOT_URL,
                DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW,
                HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW));
        addEnricher(SensorTransformingEnricher.newInstanceTransforming(web, 
                DynamicWebAppCluster.GROUP_SIZE, Functions.<Integer>identity(), APPSERVERS_COUNT));
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, WebClusterDatabaseExample.class).displayName("Brooklyn WebApp Cluster with Database example"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
