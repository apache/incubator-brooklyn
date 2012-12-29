package brooklyn.test.entity;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.trait.Startable;

/**
* Mock cluster entity for testing.
*/
public class TestCluster extends DynamicCluster {
   public int size;

   public TestCluster(Entity parent, int initialSize) {
       super(parent);
       size = initialSize;
       setAttribute(Startable.SERVICE_UP, true);
   }
   
   public TestCluster(int initialSize) {
       super((Entity)null);
       size = initialSize;
   }
   
   @Override
   public Integer resize(Integer desiredSize) {
       this.size = desiredSize;
       return size;
   }
   
   @Override
   public Integer getCurrentSize() {
       return size;
   }
}
