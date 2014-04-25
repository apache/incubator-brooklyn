package brooklyn.demo;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.riak.RiakCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

@Catalog(name = "Riak Cluster Application", description = "Riak ring deployment blueprint")
public class RiakClusterExample extends AbstractApplication {

    @CatalogConfig(label = "Riak Ring Size")
    public static final ConfigKey<Integer> RIAK_RING_SIZE = ConfigKeys.newConfigKey(
            "riak.ring.size", "Initial size of the Riak Ring", 2);

    public void init() {
        addChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, getConfig(RIAK_RING_SIZE)) );
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "jclouds:aws-ec2");
        Preconditions.checkArgument(args.isEmpty(), "Unsupported args: "+args);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
            .application(EntitySpec.create(StartableApplication.class, RiakClusterExample.class))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }
    
}
