package brooklyn.demo;

import java.util.List;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.messaging.kafka.KafkaCluster;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

/** Kafka Cluster Application */
public class KafkaClusterExample extends ApplicationBuilder {

    public static final String DEFAULT_LOCATION = "localhost";

    /** Configure the application. */
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(KafkaCluster.class)
                .configure("initialSize", 2));

        appDisplayName("Kafka cluster application");
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(new KafkaClusterExample())
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }
}
