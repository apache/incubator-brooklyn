package org.overpaas.web.jboss;

import java.util.List;
import java.util.Map;

import org.overpaas.entities.ClusterFromTemplate;
import org.overpaas.entities.Group;

public class JBossCluster extends ClusterFromTemplate {
    // TODO: Need to think about how JBoss cluster is modelled and controlled in overpaas entity
    // hierarchy. There may be a group of jboss nodes and a separate entity for the cluster.
    // How should these be related?

    public JBossCluster(Map props=[:], Group parent) {
        super(props, parent, new JBossNode())
    }
}