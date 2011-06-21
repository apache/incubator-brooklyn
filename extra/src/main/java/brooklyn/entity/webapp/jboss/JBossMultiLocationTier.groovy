package brooklyn.entity.webapp.jboss;

import java.util.Map

import brooklyn.entity.Group
import brooklyn.entity.group.Fabric

public class JBossMultiLocationTier extends Fabric {
	public JBossMultiLocationTier(Map properties=[:], Group owner=null) {
		super(properties, owner, new JBossCluster(properties, null))
	}
}
