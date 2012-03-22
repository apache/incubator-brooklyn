package org.infinispan.examples.tutorial.clustered;

import org.infinispan.Cache;

public class Node1 extends AbstractNode {

   public static void main(String[] args) throws Exception {
      new Node1().run();
   }
   
   public Node1() {
      super(1);
   }

   public void run() {
      Cache<String, String> cache = getCacheManager().getCache("Demo");

      waitForClusterToForm();

      // Put some information in the cache that we can display on the other node
      cache.put("key", "value");
   }

}
