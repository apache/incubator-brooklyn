
package brooklyn.earlythoughts;

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.group.Fabric
import brooklyn.location.Location
import brooklyn.location.basic.AbstractLocation

public class PretendLocations {
    public static class AmazonLocation extends AbstractLocation { private static final long serialVersionUID = 1L; Location parentLocation = null; }
    public static class VcloudLocation extends AbstractLocation { private static final long serialVersionUID = 1L; Location parentLocation = null; }
    public static class AmazonUsEast extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        String username, password;
    }
    
    public static class MockLocation extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        String displayName = "mock";

        public void logEvent(String event, Object entity) {
            println ""+entity+": "+event
        }
        
        @Override
        public String toString() {
            return "MockLocation["+displayName+"]";
        }
        
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((displayName == null) ? 0 : displayName.hashCode());
            return result;
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
    public static class TomcatFabric extends Fabric {
        public TomcatFabric(Map properties=[:], Group parent=null, Entity template=null) { super(properties, parent, template); }
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
