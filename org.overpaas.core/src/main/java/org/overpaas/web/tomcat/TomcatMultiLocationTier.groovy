package org.overpaas.web.tomcat;

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.types.common.Fabric

public class TomcatFabric extends Fabric/*<TomcatCluster>*/ {

	public TomcatFabric(Map properties=[:], GroupEntity parent=null) {
		super(properties, parent, new TomcatCluster(properties, null))
	}
		
}
