package org.infinispan.examples.tutorial.clustered.util;

import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Utility class that knows how to wait until a cluster forms
 * 
 * @author Pete Muir
 * 
 */
public class ClusterValidation {

   private static int REPLICATION_TRY_COUNT = 60;
   private static int REPLICATION_TIME_SLEEP = 2000;

   /**
    * The time to wait for the cluster to form
    */
   public static int TIMEOUT = REPLICATION_TIME_SLEEP * REPLICATION_TIME_SLEEP;

   private static final String KEY = ClusterValidation.class.getName();

   /**
    * Waits for the cluster to form. You must call this method on every node in
    * the cluster.
    * 
    * @param cacheManager
    *           the {@link EmbeddedCacheManager} to use to check if the cluster
    *           has formed
    * @param nodeId
    *           the id of the current node
    * @param clusterSize
    *           the number of nodes in the cluster
    * @return true if the cluster formed within the time out, otherwise false
    */
   public static boolean waitForClusterToForm(
	 EmbeddedCacheManager cacheManager, int nodeId, int clusterSize) {
      return new ClusterValidation(cacheManager.getCache(KEY), nodeId,
	    clusterSize).checkReplicationSeveralTimes() > 0;
   }
   
   private Log log = LogFactory.getLog(ClusterValidation.class);

   private final Cache<Object, Object> cache;
   private final int clusterSize;
   private final int nodeId;

   private ClusterValidation(Cache<Object, Object> cache, int nodeId,
	 int clusterSize) {
      this.cache = cache;
      this.clusterSize = clusterSize;
      this.nodeId = nodeId;
   }

   private int checkReplicationSeveralTimes() {

      for (int i = 0; i < REPLICATION_TRY_COUNT; i++) {
	 // Try to put our key into the cache
	 tryToPut();
	 // Wait for a bit to allow replication and wait for the other node to
	 // start
	 try {
	    Thread.sleep(REPLICATION_TIME_SLEEP);
	 } catch (InterruptedException e) {
	    Thread.currentThread().interrupt();
	 }
	 int replCount = replicationCount(clusterSize);
	 if (replCount == clusterSize - 1) {
	    // The cluster has formed if there are n-1 replicas in the cluster
	    log.info("Cluster formed successfully!");
	    // We try to put one last time to ensure that *our* key is now in
	    // the cluster so that other nodes see it!
	    tryToPut();
	    return replCount;
	 }

      }
      // Give up
      log.warn("Cluster failed to form!");
      return -1;
   }

   private void tryToPut() {
      int tryCount = 0;
      while (tryCount < 5) {
	 try {
	    cache.put(key(nodeId), "true");
	    return;
	 } catch (Throwable e) {
	    tryCount++;
	 }
      }
      throw new IllegalStateException(
	    "Couldn't accomplish addition before replication!");
   }

   private int replicationCount(int clusterSize) {
      int replicaCount = 0;
      for (int i = 0; i < clusterSize; i++) {
	 if (i == nodeId) {
	    continue;
	 }
	 Object data = tryGet(i);
	 if (data == null || !"true".equals(data)) {
	 } else {
	    replicaCount++;
	 }
      }
      return replicaCount;
   }

   private Object tryGet(int i) {
      int tryCont = 0;
      while (tryCont < 5) {
	 try {
	    return cache.get(key(i));
	 } catch (Throwable e) {
	    tryCont++;
	 }
      }
      return null;
   }

   private String key(int slaveIndex) {
      return KEY + slaveIndex;
   }

}
