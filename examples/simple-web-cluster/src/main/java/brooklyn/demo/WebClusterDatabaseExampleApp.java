package brooklyn.demo;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.enricher.basic.SensorTransformingEnricher;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.java.JavaEntityMethods;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
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
 * <p>
 * Demonstrates how to define a new Application Entity class (reusable and extensible),
 * as opposed to just using the builder as in {@link WebClusterDatabaseExample}.
 * With an app, when we define public static sensors and runtime config _on the app class_ (no the builder)
 * they can be discovered at runtime.
 * <p>
 * This variant also increases minimum size to 2.  
 * Note the policy min size must have the same value,
 * otherwise it fights with cluster set up trying to reduce the cluster size!
 **/
@ImplementedBy(WebClusterDatabaseExampleApp.Impl.class)
public interface WebClusterDatabaseExampleApp extends StartableApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleApp.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    BasicAttributeSensor<Integer> APPSERVERS_COUNT = new BasicAttributeSensor<Integer>(Integer.class, 
            "appservers.count", "Number of app servers deployed");
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW = 
            WebAppServiceConstants.REQUESTS_PER_SECOND_IN_WINDOW;
    public static final AttributeSensor<String> ROOT_URL = WebAppServiceConstants.ROOT_URL;

    public static class Builder extends ApplicationBuilder {
        public Builder() { super(BasicEntitySpec.newInstance(WebClusterDatabaseExampleApp.class)); }
        protected void doBuild() {
            MySqlNode mysql = createChild(BasicEntitySpec.newInstance(MySqlNode.class)
                    .configure("creationScriptUrl", DB_SETUP_SQL_URL));

            ControlledDynamicWebAppCluster web = createChild(BasicEntitySpec.newInstance(ControlledDynamicWebAppCluster.class)
                    .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                    .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                    .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                            formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                    attributeWhenReady(mysql, MySqlNode.MYSQL_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                    .configure(DynamicCluster.INITIAL_SIZE, 2));

            web.getCluster().addPolicy(AutoScalerPolicy.builder().
                    metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                    metricRange(10, 100).
                    sizeRange(2, 5).
                    build());
            
            getApplication().addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(web,  
                    WebAppServiceConstants.ROOT_URL,
                    DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW));
            getApplication().addEnricher(new SensorTransformingEnricher<Integer,Integer>(web, 
                    DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT, Functions.<Integer>identity()));
        }
    }

    public static class Impl extends AbstractApplication implements WebClusterDatabaseExampleApp {
        
        // any app-specific implementation / subclassing can be done here
        // (it's not necessary to have this, in which case you don't need the custom 
        // Builder constructor above; this is just supplied to show how it is done)
        
    }
    
    public static class Main {
        public static void main(String[] argv) {
            List<String> args = Lists.newArrayList(argv);
            String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
            String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

            BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                    .webconsolePort(port)
                    .launch();

            Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

            StartableApplication app = new WebClusterDatabaseExampleApp.Builder()
                    .appDisplayName("Brooklyn WebApp Cluster with Database example")
                    .manage(server.getManagementContext());

            app.start(ImmutableList.of(loc));

            Entities.dumpInfo(app);
        }
    }
    
}
