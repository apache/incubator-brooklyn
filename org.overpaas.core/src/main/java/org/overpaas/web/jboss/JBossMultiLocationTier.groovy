package org.overpaas.web.jboss;

import org.overpaas.core.decorators.GroupEntity
import org.overpaas.core.types.common.Fabric

import org.overpaas.core.types.common.Fabric

public class JBossMultiLocationTier extends Fabric/*<JBossCluster>*/ {

	public JBossMultiLocationTier(Map properties=[:], GroupEntity parent=null) {
		super(properties, parent, new JBossCluster(properties, null))
	}
		
}
