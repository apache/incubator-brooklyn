package brooklyn.entity.webapp.jboss;

import java.util.Map

import brooklyn.entity.Group
import brooklyn.entity.group.ClusterFromTemplate

// Refer to DynamicCluster instead.
@Deprecated
public class JBossCluster extends ClusterFromTemplate {
    // TODO: Need to think about how JBoss cluster is modelled and controlled in the entity
    // hierarchy. There may be a group of jboss nodes and a separate entity for the cluster.
    // How should these be related?

    public JBossCluster(Map props=[:], JBoss6Server template=new JBoss6Server()) {
        super(props, template)
    }
}