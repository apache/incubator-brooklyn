package brooklyn.mementos;

import java.util.List;
import java.util.Map;


public interface LocationMemento extends Memento {

    String getType();
    
    String getId();
    
    String getDisplayName();

    String getParent();
    
    List<String> getChildren();
    
    Object getProperty(String name);

	Map<String, ? extends Object> getProperties();
}
