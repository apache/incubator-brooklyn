package brooklyn.extras.whirr;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.extras.whirr.core.WhirrCluster;
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class WhirrHadoopExample extends ApplicationBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrHadoopExample.class);

    public static final String DEFAULT_LOCATION = "aws-ec2:eu-west-1";

    @Override
    protected void doBuild() {
        WhirrCluster cluster = createChild(BasicEntitySpec.newInstance(WhirrHadoopCluster.class)
                .displayName("brooklyn-hadoop-example")
                .configure("size", 2)
                .configure("memory", 2048));
    }

    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        BasicApplication app = (BasicApplication) new WhirrHadoopExample()
                .manage(server.getManagementContext());
        
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
        
        LOG.info("Press return to shut down the cluster");
        System.in.read(); //wait for the user to type a key
        app.stop();
    }
}
