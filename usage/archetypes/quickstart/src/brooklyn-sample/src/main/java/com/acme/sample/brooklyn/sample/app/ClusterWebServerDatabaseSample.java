package com.acme.sample.brooklyn.sample.app;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

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
import brooklyn.entity.database.DatabaseNode;
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
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.maven.MavenArtifact;
import brooklyn.util.maven.MavenRetriever;

import com.google.common.base.Functions;

/** This sample builds a 3-tier application with an elastic app-server cluster,
 *  and it sets it up for use in the Brooklyn catalog. 
 *  <p>
 *  Note that root access (and xcode etc) may be required to install nginx.
 **/
@Catalog(name="Elastic Java Web + DB",
    description="Deploys a WAR to a load-balanced elastic Java AppServer cluster, " +
        "with an auto-scaling policy, " +
        "wired to a database initialized with the provided SQL; " +
        "defaults to a 'Hello World' chatroom app.",
    iconUrl="classpath://com/acme/sample/brooklyn/sample-icon.png")
public class ClusterWebServerDatabaseSample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(ClusterWebServerDatabaseSample.class);

    // ---------- WAR configuration ---------------
    
    public static final String DEFAULT_WAR_URL =
            // can supply any URL -- this loads a stock example from maven central / sonatype
            MavenRetriever.localUrl(MavenArtifact.fromCoordinate("io.brooklyn.example:brooklyn-example-hello-world-sql-webapp:war:0.5.0"));

    @CatalogConfig(label="WAR (URL)", priority=2)
    public static final ConfigKey<String> WAR_URL = ConfigKeys.newConfigKey(
        "app.war", "URL to the application archive which should be deployed", DEFAULT_WAR_URL);    

    
    // ---------- DB configuration ----------------
    
    // this is included in src/main/resources. if in an IDE, ensure your build path is set appropriately.
    public static final String DEFAULT_DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    @CatalogConfig(label="DB Setup SQL (URL)", priority=1)
    public static final ConfigKey<String> DB_SETUP_SQL_URL = ConfigKeys.newConfigKey(
        "app.db_sql", "URL to the SQL script to set up the database", 
        DEFAULT_DB_SETUP_SQL_URL);
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    

    // --------- Custom Sensor --------------------
    
    AttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor( 
            "appservers.count", "Number of app servers deployed");

    
    // --------- Initialization -------------------
    
    /** Initialize our application. In this case it consists of 
     *  a single DB, with a load-balanced cluster (nginx + multiple JBosses, by default),
     *  with some sensors and a policy. */
    @Override
    public void init() {
        DatabaseNode db = addChild(
                EntitySpec.create(MySqlNode.class)
                        .configure(MySqlNode.CREATION_SCRIPT_URL, Entities.getRequiredUrlConfig(this, DB_SETUP_SQL_URL)));

        ControlledDynamicWebAppCluster web = addChild(
                EntitySpec.create(ControlledDynamicWebAppCluster.class)
                        // set WAR to use, and port to use
                        .configure(JavaWebAppService.ROOT_WAR, Entities.getRequiredUrlConfig(this, WAR_URL))
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        
//                        // optionally - use Tomcat instead of JBoss (default:
//                        .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class))
                        
                        // inject a JVM system property to point to the DB
                        .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(db, DatabaseNode.DB_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                    
                        // start with 2 appserver nodes, initially
                        .configure(DynamicCluster.INITIAL_SIZE, 2) 
                    );

        // add an enricher which measures latency
        web.addEnricher(HttpLatencyDetector.builder().
                url(WebAppServiceConstants.ROOT_URL).
                rollup(10, TimeUnit.SECONDS).
                build());

        // add a policy which scales out based on Reqs/Sec
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(2, 5).
                build());

        // add a few more sensors at the top-level (KPI's at the root of the application)
        addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(web,  
                WebAppServiceConstants.ROOT_URL,
                DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW,
                HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW));
        addEnricher(SensorTransformingEnricher.newInstanceTransforming(web, 
                DynamicWebAppCluster.GROUP_SIZE, Functions.<Integer>identity(), APPSERVERS_COUNT));
    }
    
}
