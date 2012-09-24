package brooklyn.entity.rebind;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.location.Location;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.MutableMap;

public class BasicLocationMemento implements LocationMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    private String type;
    private String id;
    private String displayName;
    private String parent;
    private List<String> children;
    private Map<String,Object> properties;

    public BasicLocationMemento(Location location) {
        this(location, Collections.<String,Object>emptyMap());
    }
    
    public BasicLocationMemento(Location location, Map<String,?> properties) {
        type = location.getClass().getName();
        id = location.getId();
        displayName = location.getName();
        
        this.properties = (properties != null) ? MutableMap.copyOf(properties) : Collections.<String,Object>emptyMap();

        Location parentLocation = location.getParentLocation();
        parent = (parentLocation != null) ? parentLocation.getId() : null;
        
        children = new ArrayList<String>(location.getChildLocations().size());
        for (Location child : location.getChildLocations()) {
            children.add(child.getId()); 
        }
        children = Collections.unmodifiableList(children);
    }

    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    };
    
    @Override
    public String getParent() {
        return parent;
    }
    
    @Override
    public List<String> getChildren() {
        return children;
    }
    
    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }
    
    @Override
    public Map<String, ? extends Object> getProperties() {
        return properties;
    }
}
