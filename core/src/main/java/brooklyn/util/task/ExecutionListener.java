package brooklyn.util.task;

import brooklyn.management.Task;

public interface ExecutionListener {

    public void onTaskDone(Task<?> task);
}
