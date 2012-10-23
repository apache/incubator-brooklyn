package brooklyn.entity.rebind;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Set;

import brooklyn.location.Location;

import com.google.common.collect.Sets;

public class TreeUtils {

    public static Collection<Location> findLocationsInHierarchy(Location root) {
        Set<Location> result = Sets.newLinkedHashSet();
        
        Deque<Location> tovisit = new ArrayDeque<Location>();
        tovisit.addFirst(root);
        
        while (tovisit.size() > 0) {
            Location current = tovisit.pop();
            result.add(current);
            for (Location child : current.getChildLocations()) {
                if (child != null) {
                    tovisit.push(child);
                }
            }
        }
        
        return result;
    }
}
