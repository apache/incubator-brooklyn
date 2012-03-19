package org.infinispan.examples.tutorial.clustered;

import org.infinispan.config.Configuration;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.examples.tutorial.clustered.util.ClusterValidation;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

public abstract class AbstractNode {
   
   public static final int CLUSTER_SIZE = 2;

   private final EmbeddedCacheManager cacheManager;
   private final int nodeId;
   
   public AbstractNode(int nodeId) {
      this.nodeId = nodeId;
      // Create the configuration, and set to replication
      GlobalConfiguration gc = GlobalConfiguration.getClusteredDefault();
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);

      // Create the cache manager and get a handle to the cache we will use
      this.cacheManager = new DefaultCacheManager(gc, c);
   }
   
   protected EmbeddedCacheManager getCacheManager() {
      return cacheManager;
   }
   
   protected void waitForClusterToForm() {
      // Wait for the cluster to form, erroring if it doesn't form after the
      // timeout
      if (!ClusterValidation.waitForClusterToForm(getCacheManager(), getNodeId(), CLUSTER_SIZE)) {
	 throw new IllegalStateException("Error forming cluster, check the log");
      }
   }
   
   protected int getNodeId()
   {
      return nodeId;
   }

}