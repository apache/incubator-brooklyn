package brooklyn.location.jclouds;

import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.domain.Location;

import com.google.common.base.Predicate;

public class JcloudsPredicates {

    public static class NodeInLocation implements Predicate<ComputeMetadata> {
        private String regionId;
        private boolean matchNullLocations;
        public NodeInLocation(String regionId, boolean matchNullLocations) {
            this.regionId = regionId;
            this.matchNullLocations = matchNullLocations;
        }
        @Override
        public boolean apply(ComputeMetadata input) {
            boolean exclude;
            Location nodeLocation = input.getLocation();
            if (nodeLocation==null) return matchNullLocations;
            
            exclude = true;
            while (nodeLocation!=null && exclude) {
                if (nodeLocation.getId().equals(regionId)) {
                    // matching location info found
                    exclude = false;
                }
                nodeLocation = nodeLocation.getParent();
            }
            return !exclude;
        }
    }
    
}
