package brooklyn.test.entity;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;

/**
* Mock cluster entity for testing.
*/
// TODO Don't want to extend EntityLocal, but tests want to call app.addPolicy
@ImplementedBy(TestClusterImpl.class)
public interface TestCluster extends DynamicCluster, EntityLocal {
}
