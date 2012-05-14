package brooklyn.extras.whirr

import java.util.List;

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities;

import org.slf4j.LoggerFactory
import org.slf4j.Logger
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location;
import brooklyn.location.basic.CommandLineLocations
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.CommandLineUtil;
import brooklyn.extras.whirr.core.WhirrCluster

public class WhirrExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrExample.class);

    public static final List<String> DEFAULT_LOCATIONS = [ "aws-ec2:eu-west-1" ]

    public static final String RECIPE = '''
whirr.cluster-name=brooklyn-whirr
whirr.hardware-min-ram=1024
whirr.instance-templates=1 noop, 1 elasticsearch
'''

    WhirrCluster cluster = new WhirrCluster(this, recipe: RECIPE)

    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = CommandLineLocations.getLocationsById(args ?: DEFAULT_LOCATIONS)
		
        try {
            def app = new WhirrExample()

            BrooklynLauncher.manage(app, port)
            app.start(locations)
            Entities.dumpInfo(app)
            LOG.info("Press return to shut down the cluster")
            System.in.read() //wait for the user to type a key
            app.stop()
        } catch (Throwable e) {
            LOG.error("Failed to start: "+e, e);
            Thread.sleep(600);
            throw e;
        }
    }

}
