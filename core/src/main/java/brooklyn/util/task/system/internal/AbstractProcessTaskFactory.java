package brooklyn.util.task.system.internal;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskStub;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

public abstract class AbstractProcessTaskFactory<T extends AbstractProcessTaskFactory<T,RET>,RET> extends ProcessTaskStub implements ProcessTaskFactory<RET> {
    
    private static final Logger log = LoggerFactory.getLogger(AbstractProcessTaskFactory.class);
    
    protected boolean dirty = false;
    
    public AbstractProcessTaskFactory(String ...commands) {
        this.commands.addAll(Arrays.asList(commands));
    }

    @SuppressWarnings("unchecked")
    protected T self() { return (T)this; }

    protected void markDirty() {
        dirty = true;
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

    public ProcessTaskFactory<Boolean> returningIsExitCodeZero() {
        if (requireExitCodeZero==null) allowingNonZeroExitCode();
        return returning(new Function<ProcessTaskWrapper<?>,Boolean>() {
            public Boolean apply(ProcessTaskWrapper<?> input) {
                return input.getExitCode()==0;
            }
        });
    }

    @SuppressWarnings({ "unchecked" })
    public AbstractProcessTaskFactory<?,String> requiringZeroAndReturningStdout() {
        requiringExitCodeZero();
        return (AbstractProcessTaskFactory<?,String>)returning(ScriptReturnType.STDOUT_STRING);
    }

    public AbstractProcessTaskFactory<?,?> returning(ScriptReturnType type) {
        markDirty();
        returnType = Preconditions.checkNotNull(type);
        return self();
    }

    @SuppressWarnings("unchecked")
    public <RET2> AbstractProcessTaskFactory<?,RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
        markDirty();
        returnType = ScriptReturnType.CUSTOM;
        this.returnResultTransformation = resultTransformation;
        return (AbstractProcessTaskFactory<?, RET2>) self();
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

    /** creates the TaskBuilder which can be further customized; typically invoked by the initial {@link #newTask()} */
    public TaskBuilder<Object> constructCustomizedTaskBuilder() {
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
 
    public T addCompletionListener(Function<ProcessTaskWrapper<?>, Void> listener) {
        completionListeners.add(listener);
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