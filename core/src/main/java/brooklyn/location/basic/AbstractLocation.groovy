package brooklyn.location.basic

import brooklyn.location.Location
import com.google.common.base.Preconditions

/**
 * Created by IntelliJ IDEA.
 * User: richard
 * Date: 04/07/2011
 * Time: 16:12
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractLocation implements Location {

    private final String name
    private Location parentLocation
    private final Collection<Location> childLocations = []
    private final Collection<Location> childLocationsReadOnly = Collections.unmodifiableCollection(childLocations)
    private Map leftoverProperties

    public AbstractLocation(Map properties = [:]) {
        if (properties.name) {
            Preconditions.checkArgument properties.name == null || properties.name instanceof String,
                "'name' property should be a string"
            name = properties.remove("name")
        }
        if (properties.parentLocation) {
            Preconditions.checkArgument properties.parentLocation == null || properties.parentLocation instanceof Location,
                "'parentLocation' property should be a Location instance"
            setParentLocation(properties.remove("parentLocation"))
        }
        leftoverProperties = properties
    }

    public String getName() { return name; }
    public Location getParentLocation() { return parentLocation; }
    public Collection<Location> getChildLocations() { return childLocationsReadOnly; }
    protected void addChildLocation(Location child) { childLocations.add(child); }
    protected boolean removeChildLocation(Location child) { return childLocations.remove(child); }

    public void setParentLocation(Location parent) {
        if (parentLocation != null) {
            parentLocation.removeChildLocation(this);
            parentLocation = null;
        }
        if (parent != null) {
            parentLocation = parent;
            parentLocation.addChildLocation(this);
        }
    }

}
