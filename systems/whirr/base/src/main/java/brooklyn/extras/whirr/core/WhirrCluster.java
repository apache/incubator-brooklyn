package brooklyn.extras.whirr.core;

import java.util.Collection;

import org.apache.whirr.Cluster;
import org.apache.whirr.ClusterController;
import org.apache.whirr.ClusterSpec;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

/**
 * Generic entity that can be used to deploy clusters that are
 * managed by Apache Whirr.
 *
 */
public interface WhirrCluster extends Entity, Startable {

    @SetFromFlag("recipe")
    public static final BasicConfigKey<String> RECIPE = new BasicConfigKey<String>(
            String.class, "whirr.recipe", "Apache Whirr cluster recipe");

    public static final BasicAttributeSensor<String> CLUSTER_NAME = new BasicAttributeSensor<String>(
            String.class, "whirr.cluster.name", "Name of the Whirr cluster");

    /**
     * Apache Whirr can only start and manage a cluster in a single location
     *
     * @param locations
     */
    @Override
    void start(Collection<? extends Location> locations);

    @Beta
    public ClusterSpec getClusterSpec();

    @Beta
    public Cluster getCluster();

    @Beta
    @VisibleForTesting
    public ClusterController getController();
}
