package brooklyn.rest.util;

import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;

public class EntityLocationUtils {

    protected final ManagementContext context;

    public EntityLocationUtils(ManagementContext ctx) {
        this.context = ctx;
    }
    
    /* Returns the number of entites at each location for which the geographic coordinates are known. */
    public Map<Location, Integer> countLeafEntitiesByLocatedLocations() {
        Map<Location, Integer> result = new LinkedHashMap<Location, Integer>();
        for (Entity e: context.getApplications()) {
            countLeafEntitiesByLocatedLocations(e, null, result);
        }
        return result;
    }

    protected void countLeafEntitiesByLocatedLocations(Entity target, Entity locatedParent, Map<Location, Integer> result) {
        if (isLocatedLocation(target))
            locatedParent = target;
        if (!target.getChildren().isEmpty()) {
            // non-leaf - inspect children
            for (Entity child: target.getChildren()) 
                countLeafEntitiesByLocatedLocations(child, locatedParent, result);
        } else {
            // leaf node - increment location count
            if (locatedParent!=null) {
                for (Location l: locatedParent.getLocations()) {
                    Location ll = getMostGeneralLocatedLocation(l);
                    if (ll!=null) {
                        Integer count = result.get(ll);
                        if (count==null) count = 1;
                        else count++;
                        result.put(ll, count);
                    }
                }
            }
        }
    }

    protected Location getMostGeneralLocatedLocation(Location l) {
        if (l==null) return null;
        if (!isLocatedLocation(l)) return null;
        Location ll = getMostGeneralLocatedLocation(l.getParent());
        if (ll!=null) return ll;
        return l;
    }

    protected boolean isLocatedLocation(Entity target) {
        for (Location l: target.getLocations())
            if (isLocatedLocation(l)) return true;
        return false;
    }
    protected boolean isLocatedLocation(Location l) {
        return l.getLocationProperty("latitude")!=null && l.getLocationProperty("longitude")!=null;
    }

}
