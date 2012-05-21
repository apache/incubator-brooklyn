package brooklyn.extras.whirr.hadoop

import brooklyn.extras.whirr.core.WhirrCluster
import brooklyn.entity.Entity
import brooklyn.util.flags.SetFromFlag
import brooklyn.event.basic.BasicConfigKey
import com.google.common.collect.Lists
import static com.google.common.base.Preconditions.checkArgument
import com.google.common.base.Joiner
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import static org.apache.whirr.RolePredicates.role
import org.apache.whirr.service.hadoop.HadoopProxy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class WhirrHadoopCluster extends WhirrCluster {

    public static final Logger log = LoggerFactory.getLogger(WhirrHadoopCluster.class);

    @SetFromFlag("name")
    public static final BasicConfigKey<String> NAME =
        [String, "whirr.hadoop.name", "The name of the Hadoop cluster"]

    @SetFromFlag("size")
    public static final BasicConfigKey<Integer> SIZE =
        [Integer, "whirr.hadoop.size", "The size of the Hadoop cluster (including a dedicated machine for the namenode)", 2]

    @SetFromFlag("memory")
    public static final BasicConfigKey<Integer> MEMORY =
        [Integer, "whirr.hadoop.memory", "The minimum amount of memory to use for each node (in megabytes)", 1024]

    public static final BasicAttributeSensor<String> NAME_NODE_URL =
        [String, "whirr.hadoop.namenodeUrl", "URL for the Hadoop name node in this cluster (hdfs://...)"]

    public static final BasicAttributeSensor<String> JOB_TRACKER_HOST_PORT =
        [String, "whirr.hadoop.jobtrackerHostPort", "Hadoop Jobtracker host and port"]

    public static final BasicAttributeSensor<String> SOCKS_SERVER =
        [String, "whirr.hadoop.socks.server", "Local SOCKS server connection details"]

    protected HadoopProxy proxy = null;

    public WhirrHadoopCluster(Map flags = [:], Entity owner = null) {
        super(flags, owner)
        generateWhirrClusterRecipe();
    }

    public void generateWhirrClusterRecipe() {
        checkArgument(getConfig(SIZE) > 1, "Min cluster size is 2")
        checkArgument(getConfig(MEMORY) >= 1000, "We need at least 1GB of memory per machine")

        List<String> recipeLines = Lists.newArrayList(
                "whirr.cluster-name=" + (((String)getConfig(NAME))?:"brooklyn-whirr-cluster").replaceAll("\\s+","-"),
                "whirr.instance-templates=1 hadoop-namenode+hadoop-jobtracker, "
                        + (getConfig(SIZE) - 1) + " hadoop-datanode+hadoop-tasktracker",
                "whirr.hardware-min-ram=" + getConfig(MEMORY)
                // TODO: set whirr.hadoop.tarball.url=... to a reliable location
        );

        if (userRecipeLines) recipeLines.addAll(userRecipeLines);
        setConfig(RECIPE, Joiner.on("\n").join(recipeLines));
    }
    
    List userRecipeLines = [];
    public void addRecipeLine(String line) {
        userRecipeLines << line;
        String r = getConfig(RECIPE) ?: "";
        if (r) r += "\n";
        r += line;
        setConfig(RECIPE, r);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        log.info("Starting local SOCKS proxy on port 6666 ...")
        proxy = new HadoopProxy(clusterSpec, cluster)
        proxy.start()

        setAttribute(SOCKS_SERVER, "localhost:6666")

        String namenodeHost = cluster.getInstanceMatching(role("hadoop-namenode")).publicHostName
        setAttribute(NAME_NODE_URL, "hdfs://" + namenodeHost + ":8020/")

        String jobtrackerHost = cluster.getInstanceMatching(role("hadoop-jobtracker")).publicHostName
        setAttribute(JOB_TRACKER_HOST_PORT, jobtrackerHost + ":8021")
    }

    @Override
    public void stop() {
        proxy?.stop()
        super.stop()
    }

}
