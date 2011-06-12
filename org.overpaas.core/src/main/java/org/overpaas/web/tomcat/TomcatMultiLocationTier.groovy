package org.overpaas.web.tomcat;

import java.util.Map;

import org.overpaas.entities.Fabric;
import org.overpaas.entities.Group;

public class TomcatFabric extends Fabric {
	public TomcatFabric(Map properties=[:], Group parent=null) {
		super(properties, parent, new TomcatCluster(properties, null))
	}
}
