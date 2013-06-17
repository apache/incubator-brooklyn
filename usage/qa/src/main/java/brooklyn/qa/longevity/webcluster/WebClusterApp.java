package brooklyn.qa.longevity.webcluster;

import java.util.List;
import java.util.Map;

import brooklyn.config.BrooklynProperties;
import brooklyn.enricher.CustomAggregatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.DoubleAttributeSensor;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Lists;

public class WebClusterApp extends ApplicationBuilder {

    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

    public static final String WAR_PATH = "classpath://hello-world.war";

    private static final long loadCyclePeriodMs = 2 * 60 * 1000L;

    @Override
    protected void doBuild() {
        final AttributeSensor<Double> sinusoidalLoad =
                new DoubleAttributeSensor("brooklyn.qa.sinusoidalLoad", "Sinusoidal server load");
        AttributeSensor<Double> averageLoad =
                new DoubleAttributeSensor("brooklyn.qa.averageLoad", "Average load in cluster");

        NginxController nginxController = createChild(BasicEntitySpec.newInstance(NginxController.class)
                // .configure("domain", "webclusterexample.brooklyn.local")
                .configure("port", "8000+"));

        JBoss7ServerFactory jbossFactory = new JBoss7ServerFactory(MutableMap.of("httpPort", "8080+", "war", WAR_PATH)) {
            public JBoss7Server newEntity2(Map flags, Entity parent) {
                JBoss7Server result = super.newEntity2(flags, parent);
                result.addEnricher(new SinusoidalLoadGenerator(sinusoidalLoad, 500L, loadCyclePeriodMs, 1d));
                return result;
            }
        };

        ControlledDynamicWebAppCluster web = createChild(BasicEntitySpec.newInstance(ControlledDynamicWebAppCluster.class)
                .displayName("WebApp cluster")
                .configure("controller", nginxController)
                .configure("initialSize", 1)
                .configure("factory", jbossFactory));


        web.getCluster().addEnricher(CustomAggregatingEnricher.newAveragingEnricher(MutableMap.of("allMembers", true), sinusoidalLoad, averageLoad));
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
                .application(new WebClusterApp().appDisplayName("Brooklyn WebApp Cluster example"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
