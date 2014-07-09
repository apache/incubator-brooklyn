package brooklyn.extras.whirr.hadoop;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.whirr.RolePredicates;
import org.apache.whirr.service.hadoop.HadoopProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.extras.whirr.core.WhirrClusterImpl;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class WhirrHadoopClusterImpl extends WhirrClusterImpl implements WhirrHadoopCluster {

    public static final Logger log = LoggerFactory.getLogger(WhirrHadoopClusterImpl.class);

    protected HadoopProxy proxy = null;

    protected final List<String> userRecipeLines = Collections.synchronizedList(Lists.<String>newArrayList());

    public WhirrHadoopClusterImpl() {
        super();
    }

    public WhirrHadoopClusterImpl(Map flags) {
        this(flags, null);
    }

    public WhirrHadoopClusterImpl(Entity parent) {
        super(MutableMap.of(), parent);
    }

    public WhirrHadoopClusterImpl(Map flags, Entity parent) {
        super(flags, parent);
    }
    
    @Override
    public void init() {
        generateWhirrClusterRecipe();
    }

    @Override
    public void generateWhirrClusterRecipe() {
        Preconditions.checkArgument(getConfig(SIZE) > 1, "Min cluster size is 2");
        Preconditions.checkArgument(getConfig(MEMORY) >= 1000, "We need at least 1GB of memory per machine");

        List<String> recipeLines = Lists.newArrayList(
                "whirr.cluster-name=" + elvis((String)getConfig(NAME), "brooklyn-whirr-cluster").replaceAll("\\s+","-"),
                "whirr.instance-templates=1 hadoop-namenode+hadoop-jobtracker, "
                        + (getConfig(SIZE) - 1) + " hadoop-datanode+hadoop-tasktracker",
                "whirr.hardware-min-ram=" + getConfig(MEMORY)
                // TODO: set whirr.hadoop.tarball.url=... to a reliable location
        );

        if (userRecipeLines.size() > 0) recipeLines.addAll(userRecipeLines);
        setConfig(RECIPE, Joiner.on("\n").join(recipeLines));
    }
    
    @Override
    public List<String> getUserRecipeLines() {
        synchronized (userRecipeLines) {
            return ImmutableList.copyOf(userRecipeLines);
        }
    }
    
    @Override
    public void addRecipeLine(String line) {
        userRecipeLines.add(checkNotNull(line, "line"));
        String r = elvis(getConfig(RECIPE), "");
        if (!Strings.isNullOrEmpty(r)) r += "\n";
        r += line;
        setConfig(RECIPE, r);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        log.info("Starting local SOCKS proxy on port 6666 ...");
        proxy = new HadoopProxy(clusterSpec, cluster);
        try {
            proxy.start();
    
            setAttribute(SOCKS_SERVER, "localhost:6666");
    
            String namenodeHost = cluster.getInstanceMatching(RolePredicates.role("hadoop-namenode")).getPublicHostName();
            setAttribute(NAME_NODE_URL, "hdfs://" + namenodeHost + ":8020/");
    
            String jobtrackerHost = cluster.getInstanceMatching(RolePredicates.role("hadoop-jobtracker")).getPublicHostName();
            setAttribute(JOB_TRACKER_HOST_PORT, jobtrackerHost + ":8021");
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void stop() {
        if (proxy != null) proxy.stop();
        super.stop();
    }
}
