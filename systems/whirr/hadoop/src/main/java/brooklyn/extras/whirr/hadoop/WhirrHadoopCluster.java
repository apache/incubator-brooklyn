package brooklyn.extras.whirr.hadoop;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.extras.whirr.core.WhirrCluster;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(WhirrHadoopClusterImpl.class)
public interface WhirrHadoopCluster extends WhirrCluster {

    public static final Logger log = LoggerFactory.getLogger(WhirrHadoopCluster.class);

    @SetFromFlag("name")
    public static final BasicConfigKey<String> NAME = new BasicConfigKey<String>(
            String.class, "whirr.hadoop.name", "The name of the Hadoop cluster");

    @SetFromFlag("size")
    public static final BasicConfigKey<Integer> SIZE = new BasicConfigKey<Integer>(
            Integer.class, "whirr.hadoop.size", "The size of the Hadoop cluster (including a dedicated machine for the namenode)", 2);

    @SetFromFlag("memory")
    public static final BasicConfigKey<Integer> MEMORY = new BasicConfigKey<Integer>(
            Integer.class, "whirr.hadoop.memory", "The minimum amount of memory to use for each node (in megabytes)", 1024);

    public static final BasicAttributeSensor<String> NAME_NODE_URL = new BasicAttributeSensor<String>(
            String.class, "whirr.hadoop.namenodeUrl", "URL for the Hadoop name node in this cluster (hdfs://...)");

    public static final BasicAttributeSensor<String> JOB_TRACKER_HOST_PORT = new BasicAttributeSensor<String>(
            String.class, "whirr.hadoop.jobtrackerHostPort", "Hadoop Jobtracker host and port");

    public static final BasicAttributeSensor<String> SOCKS_SERVER = new BasicAttributeSensor<String>(
            String.class, "whirr.hadoop.socks.server", "Local SOCKS server connection details");

    public void generateWhirrClusterRecipe();
    
    public List<String> getUserRecipeLines();
    
    public void addRecipeLine(String line);
}
