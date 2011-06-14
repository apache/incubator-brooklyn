package org.overpaas.example;

import groovy.transform.InheritConstructors

import org.overpaas.entities.Fabric
import org.overpaas.types.Location

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

	@InheritConstructors
	public static class MontereyFabric extends Fabric {
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}
	
	@InheritConstructors
	public static class GemfireFabric extends Fabric {
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}
	@InheritConstructors
	public static class JBossFabric extends Fabric {
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}
	@InheritConstructors
	public static class InfinispanFabric extends Fabric {
		public void propertyMissing(String name, value) { super.propertyMissing(name, value) }
		public Object propertyMissing(String name) { super.propertyMissing(name) }
	}

	public static class MontereyLatencyOptimisationPolicy {}
}
