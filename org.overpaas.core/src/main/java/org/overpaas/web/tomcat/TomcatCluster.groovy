package org.overpaas.web.tomcat;

import java.util.List;
import java.util.Map;

import org.overpaas.entities.ClusterFromTemplate;
import org.overpaas.entities.Group;

public class TomcatCluster extends ClusterFromTemplate {
	public TomcatCluster(Map props=[:], Group parent) {
		super(props, parent, new TomcatNode())
	}
}