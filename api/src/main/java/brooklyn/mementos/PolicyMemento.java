package brooklyn.mementos;

import java.util.Map;

public interface PolicyMemento extends Memento {

    Map<String, Object> getFlags();
}
