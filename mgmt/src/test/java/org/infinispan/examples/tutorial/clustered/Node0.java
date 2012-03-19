package org.infinispan.examples.tutorial.clustered;

import org.infinispan.Cache;


public class Node0 extends AbstractNode {

   public static void main(String[] args) throws Exception {
      new Node0().run();
   }
   
   public Node0() {
      super(0);
   }   
   public void run() {
      Cache<String, String> cache = getCacheManager().getCache("Demo");

      // Add a listener so that we can see the put from Node1
      cache.addListener(new LoggingListener());

      waitForClusterToForm();
   }
   
   @Override
   protected int getNodeId() {
      return 0;
   }

}
