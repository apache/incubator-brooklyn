package brooklyn.extras.whirr

import java.util.List;

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities;
import brooklyn.extras.whirr.core.WhirrCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location;
import brooklyn.location.basic.CommandLineLocations
import brooklyn.location.basic.LocationRegistry;
import brooklyn.util.CommandLineUtil;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster

public class WhirrHadoopExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrHadoopExample.class);

    public static final String DEFAULT_LOCATION = "aws-ec2:eu-west-1"

    WhirrCluster cluster = new WhirrHadoopCluster(this, size: 2, memory: 2048, name: "brooklyn-hadoop-example")

    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])

        def app = new WhirrHadoopExample()

        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }

}
