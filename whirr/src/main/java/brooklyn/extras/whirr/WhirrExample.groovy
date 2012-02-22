package brooklyn.extras.whirr

import brooklyn.entity.basic.AbstractApplication
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.CommandLineLocations
import brooklyn.extras.whirr.core.WhirrCluster

public class WhirrExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrExample.class);

    public static final String RECIPE_ZOOKEEPER = '''
whirr.cluster-name=brooklyn-whirr
whirr.hardware-id=t1.micro
whirr.instance-templates= 1 noop, 1 elasticsearch
'''
    WhirrCluster cluster = new WhirrCluster(recipe: RECIPE_ZOOKEEPER, this)

    public static void main(String[] args) {
        WhirrExample app = new WhirrExample()
        BrooklynLauncher.manage(app)

        LOG.info("Starting ZooKeeper cluster ...")
        app.start([
                CommandLineLocations.newAwsLocationFactory().newLocation("eu-west-1")
                // CommandLineLocations.newAwsLocationFactory().newLocation("us-west-1")
        ])

        LOG.info("Press enter to exit")
        System.in.read()

        LOG.info("Destroying cluster ...")
        app.stop()

        System.exit(0)
    }
}
