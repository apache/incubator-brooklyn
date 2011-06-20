package brooklyn.entity.webapp.tomcat;

import java.util.Map

import brooklyn.entity.Group
import brooklyn.entity.group.Fabric

public class TomcatFabric extends Fabric {
	public TomcatFabric(Map properties=[:], Group owner=null) {
		super(properties, owner, new TomcatCluster(properties, null))
	}
}
