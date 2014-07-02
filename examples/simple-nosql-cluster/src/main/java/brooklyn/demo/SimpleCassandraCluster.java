package brooklyn.demo;

import java.util.List;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.cassandra.CassandraDatacenter;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class SimpleCassandraCluster extends AbstractApplication {

    private static final String DEFAULT_LOCATION = "localhost";

    @Override
    public void init() {
        addChild(EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraDatacenter.INITIAL_SIZE, 1)
                .configure(CassandraDatacenter.CLUSTER_NAME, "Brooklyn"));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);
        
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, SimpleCassandraCluster.class)
                         .displayName("Cassandra"))
                         .webconsolePort(port)
                 .location(location)
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
