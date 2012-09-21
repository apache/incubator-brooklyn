package brooklyn.entity.rebind;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import brooklyn.location.Location;
import brooklyn.mementos.LocationMemento;

public class BasicLocationMemento implements LocationMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    private String type;
    private String id;
    private List<String> children;
    
    public BasicLocationMemento(Location location) {
        id = location.getId();
        
        children = new ArrayList<String>(location.getChildLocations().size());
        for (Location child : location.getChildLocations()) {
            children.add(child.getId()); 
        }
        children = Collections.unmodifiableList(children);
    }

    public String getType() {
        return type;
    }
    
    public String getId() {
        return id;
    }
    
    public List<String> getChildren() {
        return children;
    }
}
