package brooklyn.util.task.system;

import brooklyn.util.task.system.internal.SystemProcessTaskFactory.ConcreteSystemProcessTaskFactory;

public class SystemTasks {

    public static ProcessTaskFactory<Integer> exec(String ...commands) {
        return new ConcreteSystemProcessTaskFactory<Integer>(commands);
    }

}
