package org.infinispan.examples.tutorial.clustered;

import org.infinispan.Cache;

public class NumNutNode1 extends AbstractNode {

   public static void main(String[] args) throws Exception {
      new NumNutNode1().run();
   }
   
   public NumNutNode1() {
      super(1);
   }

   public void run() throws InterruptedException {

      waitForClusterToForm();
      Cache<String, String> cache = getCacheManager().getCache("Demo");

      // Put some information in the cache that we can display on the other node
      cache.put("key", "value");
      
      while (true) {
          Thread.sleep(1000);
          String k = "K"+((int)(10*Math.random()));
          String v = "V"+((int)(10*Math.random()));
          cache.put(k, v);
          System.out.println("Node1: "+k+"="+v);
      }
   }

}
