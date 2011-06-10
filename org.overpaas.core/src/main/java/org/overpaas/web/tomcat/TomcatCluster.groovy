package org.overpaas.web.tomcat;

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.types.common.ClusterFromTemplate

public class TomcatCluster extends ClusterFromTemplate {
	
	public TomcatCluster(Map props=[:], GroupEntity parent) {
		super(props, parent, new TomcatNode())
	}
	
	public List shrink(int arg0) {
		throw new UnsupportedOperationException("shrink not implemented yet")
	}
}