package brooklyn.management;

import java.util.List;


public interface HasTaskChildren<T> {

    public Iterable<Task<? extends T>> getChildrenTasks();
    
}
