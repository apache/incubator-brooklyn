package brooklyn.extras.whirr

import brooklyn.entity.basic.AbstractApplication
import brooklyn.extras.whirr.core.WhirrCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.CommandLineLocations
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster

public class WhirrHadoopExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrHadoopExample.class);

    WhirrCluster cluster = new WhirrHadoopCluster(size: 2, memory: 1024, name: "brooklyn-hadoop", this)

    public static void main(String[] args) {
        WhirrHadoopExample app = new WhirrHadoopExample()
        BrooklynLauncher.manage(app)

        LOG.info("Starting Hadoop cluster ...")
        app.start([CommandLineLocations.newAwsLocationFactory().newLocation("eu-west-1")])

        LOG.info("Press enter to exit")
        System.in.read()

        LOG.info("Destroying cluster ...")
        app.stop()

        System.exit(0)
    }
}
