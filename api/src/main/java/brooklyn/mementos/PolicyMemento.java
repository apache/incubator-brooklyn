package brooklyn.mementos;

import java.util.Map;

public interface PolicyMemento extends Memento {

    String getId();
    
    String getType();
    
    String getDisplayName();

    Map<String, Object> getFlags();

    Object getCustomProperty(String name);

	Map<String, ? extends Object> getCustomProperties();

}
