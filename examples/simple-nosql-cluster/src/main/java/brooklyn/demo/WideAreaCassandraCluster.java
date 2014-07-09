package brooklyn.demo;

import java.util.Arrays;
import java.util.List;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.cassandra.CassandraDatacenter;
import brooklyn.entity.nosql.cassandra.CassandraFabric;
import brooklyn.entity.nosql.cassandra.CassandraNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

@Catalog(name="Wide Area Cassandra Cluster Application", description="Deploy a Cassandra cluster across multiple geographies.")
public class WideAreaCassandraCluster extends AbstractApplication {

    public static final String DEFAULT_LOCATION_SPEC = "aws-ec2:us-east-1,rackspace-cloudservers-uk";
	
	@CatalogConfig(label="Initial Cluster Size (per location)", priority=2)
    public static final ConfigKey<Integer> CASSANDRA_CLUSTER_SIZE = ConfigKeys.newConfigKey(
        "cassandra.cluster.initialSize", "Initial size of the Cassandra clusterss", 2);      
    
    
	
    @Override
    public void init() {
        addChild(EntitySpec.create(CassandraFabric.class)
                .configure(CassandraDatacenter.CLUSTER_NAME, "Brooklyn")
                .configure(CassandraDatacenter.INITIAL_SIZE, getConfig(CASSANDRA_CLUSTER_SIZE)) // per location
                .configure(CassandraDatacenter.ENDPOINT_SNITCH_NAME, "brooklyn.entity.nosql.cassandra.customsnitch.MultiCloudSnitch")
                .configure(CassandraNode.CUSTOM_SNITCH_JAR_URL, "classpath://brooklyn/entity/nosql/cassandra/cassandra-multicloud-snitch.jar")
                .configure(CassandraFabric.MEMBER_SPEC, EntitySpec.create(CassandraDatacenter.class)
                        .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(CassandraNode.class)
                                .policy(PolicySpec.create(ServiceFailureDetector.class))
                                .policy(PolicySpec.create(ServiceRestarter.class)
                                        .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED)))
                        .policy(PolicySpec.create(ServiceReplacer.class)
                                .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED))));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String locations = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION_SPEC);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, WideAreaCassandraCluster.class)
                         .displayName("Cassandra"))
                 .webconsolePort(port)
                 .locations(Arrays.asList(locations))
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
