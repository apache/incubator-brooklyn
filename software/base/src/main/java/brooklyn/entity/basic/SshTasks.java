package brooklyn.entity.basic;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SshTasks.SshTaskStub.ScriptReturnType;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.management.TaskFactory;
import brooklyn.management.TaskWrapper;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class SshTasks {

    private static final Logger log = LoggerFactory.getLogger(SshTasks.class);
    
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
    
    public static SshTaskFactory<Integer> newTaskFactory(SshMachineLocation machine, String ...commands) {
        return new PlainSshTaskFactory<Integer>(machine, commands);
    }

    public static class SshTaskStub {
        protected final List<String> commands = new ArrayList<String>();
        protected SshMachineLocation machine;
        
        // config data
        protected String summary;
        protected final ConfigBag config = ConfigBag.newInstance();
        
        public static enum ScriptReturnType { CUSTOM, EXIT_CODE, STDOUT_STRING, STDOUT_BYTES, STDERR_STRING, STDERR_BYTES }
        protected Function<SshTaskWrapper<?>, ?> returnResultTransformation = null;
        protected ScriptReturnType returnType = ScriptReturnType.EXIT_CODE;
        
        protected boolean runAsScript = false;
        protected boolean runAsRoot = false;
        protected boolean requireExitCodeZero = false;
        protected String extraErrorMessage = null;
        protected Map<String,String> shellEnvironment = new MutableMap<String, String>();

        public SshTaskStub() {}
        
        protected SshTaskStub(SshTaskStub source) {
            commands.addAll(source.commands);
            machine = source.getMachine();
            summary = source.getSummary();
            config.copy(source.config);
            returnResultTransformation = source.returnResultTransformation;
            returnType = source.returnType;
            runAsScript = source.runAsScript;
            runAsRoot = source.runAsRoot;
            requireExitCodeZero = source.requireExitCodeZero;
            extraErrorMessage = source.extraErrorMessage;
            shellEnvironment.putAll(source.getShellEnvironment());
        }

        public String getSummary() {
            if (summary!=null) return summary;
            return Strings.join(commands, " ; ");
        }
        
        public SshMachineLocation getMachine() {
            return machine;
        }
        
        public Map<String, String> getShellEnvironment() {
            return ImmutableMap.copyOf(shellEnvironment);
        }
        
        // if other getters are needed they could be added
    }
    
    // cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
    public static class AbstractSshTaskFactory<T extends AbstractSshTaskFactory<T,RET>,RET> extends SshTaskStub implements SshTaskFactory<RET> {
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
        public synchronized SshTaskWrapper<RET> newTask() {
            return new SshTaskWrapper<RET>(this);
        }

        /** creates the TaskBuilder which can be further customized; typically invoked by the initial getTask */
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
    
    public static class SshTaskWrapper<RET> extends SshTaskStub implements TaskWrapper<RET> {

        private Task<RET> task;

        // execution details
        protected ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        protected ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        protected Integer exitCode = null;
        
        @SuppressWarnings("unchecked")
        private SshTaskWrapper(AbstractSshTaskFactory<?,RET> constructor) {
            super(constructor);
            TaskBuilder<Object> tb = constructor.constructCustomizedTaskBuilder();
            if (stdout!=null) tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDOUT, stdout));
            if (stderr!=null) tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDERR, stderr));
            task = (Task<RET>) tb.body(new SshJob()).build();
        }
        
        public Task<RET> asTask() {
            return getTask();
        }
        
        @Override
        public Task<RET> getTask() {
            return task;
        }
        
        public Integer getExitCode() {
            return exitCode;
        }
        
        public byte[] getStdoutBytes() {
            if (stdout==null) return null;
            return stdout.toByteArray();
        }
        
        public byte[] getStderrBytes() {
            if (stderr==null) return null;
            return stderr.toByteArray();
        }
        
        public String getStdout() {
            if (stdout==null) return null;
            return stdout.toString();
        }
        
        public String getStderr() {
            if (stderr==null) return null;
            return stderr.toString();
        }
        
        private class SshJob implements Callable<Object> {
            @Override
            public Object call() throws Exception {
                Preconditions.checkNotNull(getMachine(), "machine");
                
                ConfigBag config = ConfigBag.newInstanceCopying(SshTaskWrapper.this.config);
                if (stdout!=null) config.put(SshTool.PROP_OUT_STREAM, stdout);
                if (stderr!=null) config.put(SshTool.PROP_ERR_STREAM, stderr);
                
                if (runAsRoot)
                    config.put(SshTool.PROP_RUN_AS_ROOT, true);

                if (runAsScript)
                    exitCode = getMachine().execScript(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
                else
                    exitCode = getMachine().execCommands(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
                
                if (requireExitCodeZero && exitCode!=0)
                    throw new IllegalStateException(
                            (extraErrorMessage!=null ? extraErrorMessage+": " : "")+
                            "ssh job ended with exit code "+exitCode+" when 0 was required, in "+Tasks.current()+": "+getSummary());

                switch (returnType) {
                case CUSTOM: return returnResultTransformation.apply(SshTaskWrapper.this);
                case STDOUT_STRING: return stdout.toString();
                case STDOUT_BYTES: return stdout.toByteArray();
                case STDERR_STRING: return stderr.toString();
                case STDERR_BYTES: return stderr.toByteArray();
                case EXIT_CODE: return exitCode;
                }

                throw new IllegalStateException("Unknown return type for ssh job "+getSummary()+": "+returnType);
            }
        }
    }
    
    /** the "Plain" class exists purely so we can massage return types for callers' convenience */
    public static class PlainSshTaskFactory<RET> extends AbstractSshTaskFactory<PlainSshTaskFactory<RET>,RET> {
        /** constructor where machine will be added later */
        public PlainSshTaskFactory(String ...commands) {
            super(commands);
        }

        /** convenience constructor to supply machine immediately */
        public PlainSshTaskFactory(SshMachineLocation machine, String ...commands) {
            this(commands);
            machine(machine);
        }

        @SuppressWarnings("unchecked")
        @Override
        public PlainSshTaskFactory<String> requiringZeroAndReturningStdout() {
            return (PlainSshTaskFactory<String>) super.requiringZeroAndReturningStdout();
        }

        @SuppressWarnings("unchecked")
        public <RET2> PlainSshTaskFactory<RET2> returning(Function<SshTaskWrapper<?>, RET2> resultTransformation) {
            return (PlainSshTaskFactory<RET2>) super.returning(resultTransformation);
        }
    }

    public static SshTaskFactory<Boolean> dontRequireTtyForSudo(SshMachineLocation machine, final boolean requireSuccess) {
        return newTaskFactory(machine, CommonCommands.dontRequireTtyForSudo())
            .summary("setting up sudo")
            .configure(SshTool.PROP_ALLOCATE_PTY, true)
            .returning(new Function<SshTaskWrapper<?>,Boolean>() { public Boolean apply(SshTaskWrapper<?> task) {
                if (task.getExitCode()==0) return true;
                log.warn("Error setting up sudo for "+task.getMachine().getUser()+"@"+task.getMachine().getAddress().getHostName()+": "+
                        Strings.trim(Strings.isNonBlank(task.getStderr()) ? task.getStderr() : task.getStdout()) + 
                        " (exit code "+task.getExitCode()+")");
                if (requireSuccess) {
                    throw new IllegalStateException("Passwordless sudo is required for "+task.getMachine().getUser()+"@"+task.getMachine().getAddress().getHostName());
                }
                return true; 
            } });
    }

}
