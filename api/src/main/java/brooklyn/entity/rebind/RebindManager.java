package brooklyn.entity.rebind;

import java.util.List;

import brooklyn.entity.Application;
import brooklyn.mementos.BrooklynMemento;

public interface RebindManager {
    public BrooklynMemento getMemento();

    public List<Application> rebind(final BrooklynMemento memento);
    
    public List<Application> rebind(final BrooklynMemento memento, ClassLoader classLoader);
}
