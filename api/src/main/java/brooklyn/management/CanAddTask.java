package brooklyn.management;

import com.google.common.annotations.Beta;

/** marks a place where tasks can be added, e.g. a task which allows children to be added (including after it is activated) */
@Beta // in 0.6.0
public interface CanAddTask {

    public void addTask(Task<?> t);
    
}
