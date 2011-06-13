package org.overpaas.web.tomcat;

import groovy.transform.InheritConstructors;

import java.util.List;
import java.util.Map;

import org.overpaas.entities.Entity;
import org.overpaas.entities.ClusterFromTemplate;
import org.overpaas.entities.Group;

public class TomcatCluster extends ClusterFromTemplate {
	public TomcatCluster(Map props=[:], Group parent=null, TomcatNode template=new TomcatNode()) {
		super(props, parent, template);
	}
}