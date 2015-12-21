package com.acme.sample.brooklyn.sample.app;

import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.enricher.stock.SensorPropagatingEnricher;
import org.apache.brooklyn.enricher.stock.SensorTransformingEnricher;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.java.JavaEntityMethods;
import org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.DynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.entity.webapp.WebAppServiceConstants;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.policy.enricher.HttpLatencyDetector;
import org.apache.brooklyn.util.maven.MavenArtifact;
import org.apache.brooklyn.util.maven.MavenRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;

import static org.apache.brooklyn.core.sensor.DependentConfiguration.attributeWhenReady;
import static org.apache.brooklyn.core.sensor.DependentConfiguration.formatString;


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
    iconUrl="classpath://sample-icon.png")
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
        DatastoreCommon db = addChild(
                EntitySpec.create(MySqlNode.class)
                        .configure(MySqlNode.CREATION_SCRIPT_URL, Entities.getRequiredUrlConfig(this, DB_SETUP_SQL_URL)));

        ControlledDynamicWebAppCluster web = addChild(
                EntitySpec.create(ControlledDynamicWebAppCluster.class)
                        // set WAR to use, and port to use
                        .configure(JavaWebAppService.ROOT_WAR, getConfig(WAR_URL))
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        
//                        // optionally - use Tomcat instead of JBoss (default:
//                        .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class))
                        
                        // inject a JVM system property to point to the DB
                        .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(db, DatastoreCommon.DATASTORE_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                    
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
