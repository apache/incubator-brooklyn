package org.overpaas.web.jboss;

import java.util.List;

import org.overpaas.core.decorators.GroupEntity
import org.overpaas.core.types.common.ClusterFromTemplate

public class JBossCluster extends ClusterFromTemplate {

    /* TODO: Need to think about how JBoss cluster is modelled and controlled in overpaas entity
     * hierarchy. There may be a group of jboss nodes and a separate entity for the cluster.
     * How should these be related?
     */

    public JBossCluster(Map props=[:], GroupEntity parent) {
        super(props, parent, new JBossNode())
    }
    public List shrink(int arg0) {
        throw new UnsupportedOperationException("shrink not implemented yet")
    }
}