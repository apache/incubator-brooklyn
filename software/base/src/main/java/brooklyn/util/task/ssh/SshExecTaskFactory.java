package brooklyn.util.task.ssh;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskFactory;
import brooklyn.util.task.ssh.SshExecTaskStub.ScriptReturnType;

import com.google.common.base.Function;

public interface SshExecTaskFactory<T> extends TaskFactory<SshExecTaskWrapper<T>> {
    public SshExecTaskFactory<T> machine(SshMachineLocation machine);
    public SshExecTaskFactory<T> add(String ...commandsToAdd);
    public SshExecTaskFactory<T> add(Iterable<String> commandsToAdd);
    public SshExecTaskFactory<T> requiringExitCodeZero();
    public SshExecTaskFactory<T> requiringExitCodeZero(String extraErrorMessage);
    public SshExecTaskFactory<T> allowingNonZeroExitCode();
    public SshExecTaskFactory<String> requiringZeroAndReturningStdout();
    public SshExecTaskFactory<?> returning(ScriptReturnType type);
    public <RET2> SshExecTaskFactory<RET2> returning(Function<SshExecTaskWrapper<?>, RET2> resultTransformation);
    public SshExecTaskFactory<T> runAsCommand();
    public SshExecTaskFactory<T> runAsScript();
    public SshExecTaskFactory<T> runAsRoot();
    public SshExecTaskFactory<T> environmentVariable(String key, String val);
    public SshExecTaskFactory<T> environmentVariables(Map<String,String> vars);
    public SshExecTaskFactory<T> summary(String summary);
    public <V> SshExecTaskFactory<T> configure(ConfigKey<V> key, V value);
    
    /** adds a listener which will be notified of (otherwise) successful completion,
     * typically used to invalidate the result (ie throw exception, to promote a string in the output to an exception) */
    public SshExecTaskFactory<T> addCompletionListener(Function<SshExecTaskWrapper<?>, Void> function);
}
