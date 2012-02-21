package brooklyn.extras.whirr.hadoop

import brooklyn.extras.whirr.core.WhirrCluster
import brooklyn.entity.Entity

public class WhirrHadoopCluster extends WhirrCluster {

    public WhirrHadoopCluster(Map flags = [:], Entity owner = null) {
        super(flags, owner)
    }
}
