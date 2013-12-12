package brooklyn.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.messaging.storm.StormDeployment;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * Includes some advanced features such as KPI / derived sensors,
 * and annotations for use in a catalog.
 * <p>
 * This variant also increases minimum size to 2.  
 * Note the policy min size must have the same value,
 * otherwise it fights with cluster set up trying to reduce the cluster size!
 **/
@Catalog(name="Storm Sample App",
description="Creates a Storm analytics cluster",
    iconUrl="classpath://brooklyn/demo/glossy-3d-blue-web-icon.png")
public class StormSampleApp extends AbstractApplication implements StartableApplication {

    public static final Logger LOG = LoggerFactory.getLogger(StormSampleApp.class);

    public static final String DEFAULT_LOCATION = "named:gce-europe-west1";

    @Override
    public void init() {
        addChild(EntitySpec.create(StormDeployment.class)
            .configure(StormDeployment.SUPERVISORS_COUNT, 2)
            .configure(StormDeployment.ZOOKEEPERS_COUNT, 1));
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
            .application(EntitySpec.create(StartableApplication.class, StormSampleApp.class)
                .displayName("Storm App"))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }
}
