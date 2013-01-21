package brooklyn.extras.whirr.hadoop

import org.apache.whirr.RolePredicates
import org.apache.whirr.service.hadoop.HadoopProxy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.extras.whirr.core.WhirrClusterImpl
import brooklyn.location.Location

import com.google.common.base.Joiner
import com.google.common.base.Preconditions
import com.google.common.collect.Lists

public class WhirrHadoopClusterImpl extends WhirrClusterImpl implements WhirrHadoopCluster {

    public static final Logger log = LoggerFactory.getLogger(WhirrHadoopClusterImpl.class);

    protected HadoopProxy proxy = null;

    public WhirrHadoopClusterImpl(Map flags = [:], Entity parent = null) {
        super(flags, parent)
        generateWhirrClusterRecipe();
    }

    @Override
    public void generateWhirrClusterRecipe() {
        Preconditions.checkArgument(getConfig(SIZE) > 1, "Min cluster size is 2")
        Preconditions.checkArgument(getConfig(MEMORY) >= 1000, "We need at least 1GB of memory per machine")

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

    @Override
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

        String namenodeHost = cluster.getInstanceMatching(RolePredicates.role("hadoop-namenode")).publicHostName
        setAttribute(NAME_NODE_URL, "hdfs://" + namenodeHost + ":8020/")

        String jobtrackerHost = cluster.getInstanceMatching(RolePredicates.role("hadoop-jobtracker")).publicHostName
        setAttribute(JOB_TRACKER_HOST_PORT, jobtrackerHost + ":8021")
    }

    @Override
    public void stop() {
        proxy?.stop()
        super.stop()
    }

}
