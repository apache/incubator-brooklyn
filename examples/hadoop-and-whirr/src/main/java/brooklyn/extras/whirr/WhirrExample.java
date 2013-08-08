package brooklyn.extras.whirr;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.extras.whirr.core.WhirrCluster;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class WhirrExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrExample.class);

    public static final String DEFAULT_LOCATION = "aws-ec2:eu-west-1";

    public static final String RECIPE =
            "whirr.cluster-name=brooklyn-whirr"+"\n"+
            "whirr.hardware-min-ram=1024"+"\n"+
            "whirr.instance-templates=1 noop, 1 elasticsearch"+"\n";

    @Override
    public void init() {
        addChild(EntitySpec.create(WhirrCluster.class)
                .configure("recipe", RECIPE));
    }

    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpecs.appSpec(WhirrExample.class))
                .webconsolePort(port)
                .location(location)
                .start();
         
        StartableApplication app = (StartableApplication) launcher.getApplications().get(0);
        Entities.dumpInfo(app);
        
        LOG.info("Press return to shut down the cluster");
        System.in.read(); //wait for the user to type a key
        app.stop();
    }
}
