package brooklyn.qa.longevity.webcluster;

import java.util.List;

import brooklyn.config.BrooklynProperties;
import brooklyn.enricher.CustomAggregatingEnricher;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Lists;

public class WebClusterApp extends AbstractApplication {

    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

    public static final String WAR_PATH = "classpath://hello-world.war";

    private static final long loadCyclePeriodMs = 2 * 60 * 1000L;

    @Override
    public void init() {
        final AttributeSensor<Double> sinusoidalLoad =
                Sensors.newDoubleSensor("brooklyn.qa.sinusoidalLoad", "Sinusoidal server load");
        AttributeSensor<Double> averageLoad =
                Sensors.newDoubleSensor("brooklyn.qa.averageLoad", "Average load in cluster");

        NginxController nginxController = addChild(EntitySpec.create(NginxController.class)
                // .configure("domain", "webclusterexample.brooklyn.local")
                .configure("port", "8000+"));

        EntitySpec<JBoss7Server> jbossSpec = EntitySpec.create(JBoss7Server.class)
                .configure("httpPort", "8080+")
                .configure("war", WAR_PATH)
                .enricher(EnricherSpec.create(SinusoidalLoadGenerator.class)
                        .configure(SinusoidalLoadGenerator.TARGET, sinusoidalLoad)
                        .configure(SinusoidalLoadGenerator.PUBLISH_PERIOD_MS, 500L)
                        .configure(SinusoidalLoadGenerator.SIN_PERIOD_MS, loadCyclePeriodMs)
                        .configure(SinusoidalLoadGenerator.SIN_AMPLITUDE, 1d));

        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .displayName("WebApp cluster")
                .configure("controller", nginxController)
                .configure("initialSize", 1)
                .configure("memberSpec", jbossSpec));


        web.getCluster().addEnricher(CustomAggregatingEnricher.newAveragingEnricher(MutableMap.of("allMembers", true), sinusoidalLoad, averageLoad, null, null));
        web.getCluster().addPolicy(AutoScalerPolicy.builder()
                .metric(averageLoad)
                .sizeRange(1, 3)
                .metricRange(0.3, 0.7)
                .build());
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, WebClusterApp.class).displayName("Brooklyn WebApp Cluster example"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
