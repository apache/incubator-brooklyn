package brooklyn.demo;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.enricher.HttpLatencyDetector;
import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.enricher.basic.SensorTransformingEnricher;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.java.JavaEntityMethods;
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
import brooklyn.util.BrooklynMavenArtifacts;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.ResourceUtils;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * Includes some advanced features such as KPI / derived sensors,
 * and annotations for use in a catalog.
 * <p>
 * This variant also increases minimum size to 2.  
 * Note the policy min size must have the same value,
 * otherwise it fights with cluster set up trying to reduce the cluster size!
 **/
@Catalog(name="Elastic Java Web + DB",
    description="Deploys a WAR to a load-balanced elastic Java AppServer cluster, " +
    		"with an auto-scaling policy, " +
    		"wired to a database initialized with the provided SQL; " +
    		"defaults to a 'Hello World' chatroom app.",
    iconUrl="classpath://brooklyn/demo/glossy-3d-blue-web-icon.png")
public class WebClusterDatabaseExampleApp extends AbstractApplication implements StartableApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleApp.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static final String DEFAULT_WAR_PATH = ResourceUtils.create(WebClusterDatabaseExampleApp.class)
            // take this war, from the classpath, or via maven if not on the classpath
            .firstAvailableUrl(
                    "classpath://hello-world-sql-webapp.war",
                    BrooklynMavenArtifacts.localUrl("example", "brooklyn-example-hello-world-sql-webapp", "war"))
            .or("classpath://hello-world-sql-webapp.war");
    
    @CatalogConfig(label="WAR (URL)", priority=2)
    public static final ConfigKey<String> WAR_PATH = ConfigKeys.newConfigKey(
        "app.war", "URL to the application archive which should be deployed", 
        DEFAULT_WAR_PATH);    

    // TODO to expose in catalog we need to let the keystore url be specified (not hard)
    // and also confirm that this works for nginx (might be a bit fiddly);
    // booleans in the gui are working (With checkbox)
    @CatalogConfig(label="HTTPS")
    public static final ConfigKey<Boolean> USE_HTTPS = ConfigKeys.newConfigKey(
            "app.https", "Whether the application should use HTTPS only or just HTTP only (default)", false);
    
    public static final String DEFAULT_DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    @CatalogConfig(label="DB Setup SQL (URL)", priority=1)
    public static final ConfigKey<String> DB_SETUP_SQL_URL = ConfigKeys.newConfigKey(
        "app.db_sql", "URL to the SQL script to set up the database", 
        DEFAULT_DB_SETUP_SQL_URL);
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    AttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor( 
            "appservers.count", "Number of app servers deployed");
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW = 
            WebAppServiceConstants.REQUESTS_PER_SECOND_IN_WINDOW;
    public static final AttributeSensor<String> ROOT_URL = WebAppServiceConstants.ROOT_URL;

    @Override
    public void init() {
        MySqlNode mysql = addChild(
                EntitySpec.create(MySqlNode.class)
                        .configure(MySqlNode.CREATION_SCRIPT_URL, Entities.getRequiredUrlConfig(this, DB_SETUP_SQL_URL)));

        ControlledDynamicWebAppCluster web = addChild(
                EntitySpec.create(ControlledDynamicWebAppCluster.class)
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        // to specify a diferrent appserver:
//                        .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class))
                        .configure(JavaWebAppService.ROOT_WAR, Entities.getRequiredUrlConfig(this, WAR_PATH))
                        .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(mysql, MySqlNode.DATASTORE_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                        .configure(DynamicCluster.INITIAL_SIZE, 2)
                        .configure(WebAppService.ENABLED_PROTOCOLS, Arrays.asList(getConfig(USE_HTTPS) ? "https" : "http")) );

        web.addEnricher(HttpLatencyDetector.builder().
                url(ROOT_URL).
                rollup(10, TimeUnit.SECONDS).
                build());
        
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(2, 5).
                build());

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
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, WebClusterDatabaseExampleApp.class)
                         .displayName("Brooklyn WebApp Cluster with Database example"))
                 .webconsolePort(port)
                 .location(location)
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
