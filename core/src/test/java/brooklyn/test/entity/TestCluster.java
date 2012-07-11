package brooklyn.test.entity;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;

/**
* Mock cluster entity for testing.
*/
public class TestCluster extends DynamicCluster {
   public int size;

   public TestCluster(Entity owner, int initialSize) {
       super(owner);
       size = initialSize;
   }
   
   public TestCluster(int initialSize) {
       super((Entity)null);
       size = initialSize;
   }
           
   @Override
   public Integer getCurrentSize() {
       return size;
   }
}
