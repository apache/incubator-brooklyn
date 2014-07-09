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
            for (Location child : current.getChildren()) {
                if (child != null) {
                    tovisit.push(child);
                }
            }
        }

        Location parentLocation = root.getParent();
        while (parentLocation != null) {
            result.add(parentLocation);
            parentLocation = parentLocation.getParent();
        }

        return result;
    }
}
