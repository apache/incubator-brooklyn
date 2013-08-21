package brooklyn.util.task.ssh;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

// cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
public class AbstractSshTaskFactory<T extends AbstractSshTaskFactory<T,RET>,RET> extends SshTaskStub implements SshTaskFactory<RET> {
    
    private static final Logger log = LoggerFactory.getLogger(AbstractSshTaskFactory.class);
    
    boolean dirty = false;
    
    /** constructor where machine will be added later */
    public AbstractSshTaskFactory(String ...commands) {
        this.commands.addAll(Arrays.asList(commands));
    }

    /** convenience constructor to supply machine immediately */
    public AbstractSshTaskFactory(SshMachineLocation machine, String ...commands) {
        this(commands);
        machine(machine);
    }

    @SuppressWarnings("unchecked")
    protected T self() { return (T)this; }

    protected void markDirty() {
        dirty = true;
    }
    
    public T machine(SshMachineLocation machine) {
        markDirty();
        this.machine = machine;
        return self();
    }
        
    public T add(String ...commandsToAdd) {
        markDirty();
        for (String commandToAdd: commandsToAdd) this.commands.add(commandToAdd);
        return self();
    }

    public T add(Iterable<String> commandsToAdd) {
        Iterables.addAll(this.commands, commandsToAdd);
        return self();
    }
    
    public T requiringExitCodeZero() {
        markDirty();
        requireExitCodeZero = true;
        return self();
    }
    
    public T requiringExitCodeZero(String extraErrorMessage) {
        markDirty();
        requireExitCodeZero = true;
        this.extraErrorMessage = extraErrorMessage;
        return self();
    }
    
    public T allowingNonZeroExitCode() {
        markDirty();
        requireExitCodeZero = false;
        return self();
    }
    
    @SuppressWarnings({ "unchecked" })
    public AbstractSshTaskFactory<?,String> requiringZeroAndReturningStdout() {
        requiringExitCodeZero();
        return (AbstractSshTaskFactory<?,String>)returning(ScriptReturnType.STDOUT_STRING);
    }

    public AbstractSshTaskFactory<?,?> returning(ScriptReturnType type) {
        markDirty();
        returnType = Preconditions.checkNotNull(type);
        return self();
    }

    @SuppressWarnings("unchecked")
    public <RET2> AbstractSshTaskFactory<?,RET2> returning(Function<SshTaskWrapper<?>, RET2> resultTransformation) {
        markDirty();
        returnType = ScriptReturnType.CUSTOM;
        this.returnResultTransformation = resultTransformation;
        return (AbstractSshTaskFactory<?, RET2>) self();
    }
    
    public T runAsCommand() {
        markDirty();
        runAsScript = false;
        return self();
    }

    public T runAsScript() {
        markDirty();
        runAsScript = true;
        return self();
    }

    public T runAsRoot() {
        markDirty();
        runAsRoot = true;
        return self();
    }
    
    public T environmentVariable(String key, String val) {
        markDirty();
        shellEnvironment.put(key, val);
        return self();
    }

    public T environmentVariables(Map<String,String> vars) {
        markDirty();
        shellEnvironment.putAll(vars);
        return self();
    }

    @Override
    public SshTaskWrapper<RET> newTask() {
        dirty = false;
        return new SshTaskWrapper<RET>(this);
    }

    /** creates the TaskBuilder which can be further customized; typically invoked by the initial {@link #newTask()} */
    protected TaskBuilder<Object> constructCustomizedTaskBuilder() {
        TaskBuilder<Object> tb = TaskBuilder.builder().dynamic(false).name("ssh: "+getSummary());
        tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDIN, 
                Streams.byteArrayOfString(Strings.join(commands, "\n"))));
        return tb;
    }
    
    public T summary(String summary) {
        markDirty();
        this.summary = summary;
        return self();
    }

    public <V> T configure(ConfigKey<V> key, V value) {
        config.configure(key, value);
        return self();
    }
    
    @Override
    protected void finalize() throws Throwable {
        // help let people know of API usage error
        if (dirty)
            log.warn("Task "+this+" was modified but modification was never used");
        super.finalize();
    }
}