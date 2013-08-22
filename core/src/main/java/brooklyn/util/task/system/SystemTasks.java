package brooklyn.util.task.system;

import brooklyn.util.task.system.internal.SystemProcessTaskFactory;

public class SystemTasks {

    public static ProcessTaskFactory<Integer> exec(String ...commands) {
        return new SystemProcessTaskFactory<Integer>(commands);
    }

}
