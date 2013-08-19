package brooklyn.util.task.ssh;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskFactory;
import brooklyn.util.task.ssh.SshTaskStub.ScriptReturnType;

import com.google.common.base.Function;

public interface SshTaskFactory<T> extends TaskFactory<SshTaskWrapper<T>> {
    public SshTaskFactory<T> machine(SshMachineLocation machine);
    public SshTaskFactory<T> add(String ...commandsToAdd);
    public SshTaskFactory<T> add(Iterable<String> commandsToAdd);
    public SshTaskFactory<T> requiringExitCodeZero();
    public SshTaskFactory<T> requiringExitCodeZero(String extraErrorMessage);
    public SshTaskFactory<String> requiringZeroAndReturningStdout();
    public SshTaskFactory<?> returning(ScriptReturnType type);
    public <RET2> SshTaskFactory<RET2> returning(Function<SshTaskWrapper<?>, RET2> resultTransformation);
    public SshTaskFactory<T> runAsCommand();
    public SshTaskFactory<T> runAsScript();
    public SshTaskFactory<T> runAsRoot();
    public SshTaskFactory<T> environmentVariable(String key, String val);
    public SshTaskFactory<T> environmentVariables(Map<String,String> vars);
    public SshTaskFactory<T> summary(String summary);
    public <V> SshTaskFactory<T> configure(ConfigKey<V> key, V value);
}
