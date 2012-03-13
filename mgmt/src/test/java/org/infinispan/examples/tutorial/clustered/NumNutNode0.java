package org.infinispan.examples.tutorial.clustered;

import org.infinispan.Cache;


public class NumNutNode0 extends AbstractNode {

   public static void main(String[] args) throws Exception {
      new NumNutNode0().run();
   }
   
   public NumNutNode0() {
      super(0);
   }   
   public void run() throws InterruptedException {

      // Add a listener so that we can see the put from Node1
//      cache.addListener(new LoggingListener());

      waitForClusterToForm();
      Cache<String, String> cache = getCacheManager().getCache("Demo");
      
      while (true) {
          Thread.sleep(5000);
          String result="";
          for (int i=0; i<10; i++) result += "K"+i+"="+cache.get("K"+i)+" ";
          System.out.println("Node0: "+result);
      }
   }
   
   @Override
   protected int getNodeId() {
      return 0;
   }

}
