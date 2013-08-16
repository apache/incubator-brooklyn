package brooklyn.management;

import brooklyn.management.Task;

/** Interface for something which is not a task, but which is closely linked to one, ie. it has a task */
public interface HasTask<T> {
    Task<T> getTask();
}
