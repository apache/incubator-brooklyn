package brooklyn.test.entity;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.proxying.ImplementedBy;

/**
* Mock cluster entity for testing.
*/
//FIXME When have refactored DynamicCluster to extract interface, make this extend DynamicCluster
// TODO Don't want to extend EntityLocal, but tests want to call app.addPolicy
@ImplementedBy(TestClusterImpl.class)
public interface TestCluster extends Cluster, EntityLocal {
}
