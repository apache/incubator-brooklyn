package org.overpaas.web.jboss;

import java.util.Map;

import org.overpaas.entities.Fabric;
import org.overpaas.entities.Group;

public class JBossMultiLocationTier extends Fabric/*<JBossCluster>*/ {
	public JBossMultiLocationTier(Map properties=[:], Group parent=null) {
		super(properties, parent, new JBossCluster(properties, null))
	}
}
