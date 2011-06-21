
package brooklyn.example;

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.group.Fabric
import brooklyn.location.Location

public class PretendLocations {
	public static class AmazonLocation implements Location { }
	public static class VcloudLocation implements Location { }
	public static class AmazonUsEast implements Location {
		String username, password;
	}
	
	public static class MockLocation implements Location {
		String displayName = "mock";
		
		public void logEvent(String event, Object entity) {
			println ""+entity+": "+event
		}
		
		@Override
		public String toString() {
			return "MockLocation["+displayName+"]";
		}
		
		public boolean equals(other) {
			(other in MockLocation && displayName==other.displayName)
		}
	}

	public static class MontereyFabric extends Fabric {
		public MontereyFabric(Map properties=[:], Group parent=null, Entity template=null) { super(properties, parent, template); }
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}
	
	public static class GemfireFabric extends Fabric {
		public GemfireFabric(Map properties=[:], Group parent=null, Entity template=null) { super(properties, parent, template); }
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}
	public static class JBossFabric extends Fabric {
		public JBossFabric(Map properties=[:], Group parent=null, Entity template=null) { super(properties, parent, template); }
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}
	public static class InfinispanFabric extends Fabric {
		public InfinispanFabric(Map properties=[:], Group parent=null, Entity template=null) { super(properties, parent, template); }
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}

	public static class MontereyLatencyOptimisationPolicy {}
}
