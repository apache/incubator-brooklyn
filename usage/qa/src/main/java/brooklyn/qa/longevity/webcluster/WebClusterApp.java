package brooklyn.qa.longevity.webcluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.config.BrooklynProperties;
import brooklyn.enricher.CustomAggregatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxy.nginx.NginxControllerImpl;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.ControlledDynamicWebAppClusterImpl;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationRegistry;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.MutableMap;

public class WebClusterApp extends AbstractApplication {

    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

    public static final String DEFAULT_LOCATION = "localhost";

    public static final String WAR_PATH = "classpath://hello-world.war";

    private static final long loadCyclePeriodMs = 2 * 60 * 1000L;

    public WebClusterApp() {
        this(new LinkedHashMap());
    }

    public WebClusterApp(Map props) {
        super(props);
    }

    public static void main(String[] argv) {
        final BasicAttributeSensor<Double> sinusoidalLoad =
                new BasicAttributeSensor<Double>(Double.class, "brooklyn.qa.sinusoidalLoad", "Sinusoidal server load");
        BasicAttributeSensor<Double> averageLoad =
                new BasicAttributeSensor<Double>(Double.class, "brooklyn.qa.averageLoad", "Average load in cluster");

        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry(config).getLocationsById(!args.isEmpty() ? args : Arrays.asList(DEFAULT_LOCATION));

        WebClusterApp app = new WebClusterApp(MutableMap.of("name", "Brooklyn WebApp Cluster example"));

        NginxController nginxController = new NginxControllerImpl(
                // domain: 'webclusterexample.brooklyn.local',
                MutableMap.of("port", 8000));

        JBoss7ServerFactory jbossFactory = new JBoss7ServerFactory(MutableMap.of("httpPort", "8080+", "war", WAR_PATH)) {
            public JBoss7Server newEntity2(Map flags, Entity parent) {
                JBoss7Server result = super.newEntity2(flags, parent);
                result.addEnricher(new SinusoidalLoadGenerator(sinusoidalLoad, 500L, loadCyclePeriodMs, 1d));
                return result;
            }
        };

        ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppClusterImpl(
                MutableMap.of(
                        "name", "WebApp cluster",
                        "controller", nginxController,
                        "initialSize", 1,
                        "factory", jbossFactory), app);


        web.getCluster().addEnricher(CustomAggregatingEnricher.getAveragingEnricher(new LinkedList(), sinusoidalLoad, averageLoad));
        web.getCluster().addPolicy(AutoScalerPolicy.builder()
                .metric(averageLoad)
                .sizeRange(1, 3)
                .metricRange(0.3, 0.7)
                .build());

        BrooklynLauncher.manage(app, port);
        app.start(locations);
        Entities.dumpInfo(app);
    }
}
