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
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.HasTask;
import brooklyn.management.Task;
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
    
    public static SshTask<Integer> newInstance(SshMachineLocation machine, String ...commands) {
        return new SshTask<Integer>(machine, commands);
    }

    public static class SshTaskDetails {
        protected final List<String> commands = new ArrayList<String>();
        protected SshMachineLocation machine;
        
        // config data
        protected String summary;
        protected ConfigBag config = ConfigBag.newInstance();
        
        public static enum ScriptReturnType { CUSTOM, EXIT_CODE, STDOUT_STRING, STDOUT_BYTES, STDERR_STRING, STDERR_BYTES }
        protected ScriptReturnType returnType = ScriptReturnType.EXIT_CODE;
        
        protected boolean runAsScript = false;
        protected boolean runAsRoot = false;
        protected boolean requireExitCodeZero = false;
        protected Map<String,String> shellEnvironment = new MutableMap<String, String>();

        // execution details
        protected ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        protected ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        protected Integer exitCode = null;
        
        public SshMachineLocation getMachine() {
            return machine;
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
        
        public Map<String, String> getShellEnvironment() {
            return ImmutableMap.copyOf(shellEnvironment);
        }
        
        // if other getters are needed they could be added
    }
    
    // cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
    public static class AbstractSshTask<T extends AbstractSshTask<T,?>,RET> extends SshTaskDetails implements HasTask<RET> {
        private final SshJob job;
        private Task<Object> task;
        private Function<SshTaskDetails, ?> returnResultTransformation = null;
                
        /** constructor where machine will be added later */
        public AbstractSshTask(String ...commands) {
            this.commands.addAll(Arrays.asList(commands));
            this.job = newJob();
        }

        /** convenience constructor to supply machine immediately */
        public AbstractSshTask(SshMachineLocation machine, String ...commands) {
            this(commands);
            machine(machine);
        }

        @SuppressWarnings("unchecked")
        protected T self() { return (T)this; }

        protected void checkStillMutable() {
            Preconditions.checkState(task==null, "Cannot modify SshTask after task is gotten");
        }
        
        public T machine(SshMachineLocation machine) {
            checkStillMutable();
            this.machine = machine;
            return self();
        }
            
        public T add(String ...commandsToAdd) {
            checkStillMutable();
            for (String commandToAdd: commandsToAdd) this.commands.add(commandToAdd);
            return self();
        }

        public T add(Iterable<String> commandsToAdd) {
            Iterables.addAll(this.commands, commandsToAdd);
            return self();
        }
        
        public T requiringExitCodeZero() {
            checkStillMutable();
            requireExitCodeZero = true;
            return self();
        }
        
        @SuppressWarnings({ "unchecked" })
        public AbstractSshTask<?,String> requiringZeroAndReturningStdout() {
            requiringExitCodeZero();
            return (AbstractSshTask<?,String>)returning(ScriptReturnType.STDOUT_STRING);
        }

        public AbstractSshTask<?,?> returning(ScriptReturnType type) {
            checkStillMutable();
            returnType = Preconditions.checkNotNull(type);
            return self();
        }

        @SuppressWarnings("unchecked")
        public <RET2> AbstractSshTask<?,RET2> returning(Function<SshTaskDetails, RET2> resultTransformation) {
            checkStillMutable();
            returnType = ScriptReturnType.CUSTOM;
            this.returnResultTransformation = resultTransformation;
            return (AbstractSshTask<?, RET2>) self();
        }
        
        public T runAsCommand() {
            checkStillMutable();
            runAsScript = false;
            return self();
        }

        public T runAsScript() {
            checkStillMutable();
            runAsScript = true;
            return self();
        }

        public T runAsRoot() {
            checkStillMutable();
            runAsRoot = true;
            return self();
        }
        
        public T environmentVariable(String key, String val) {
            checkStillMutable();
            shellEnvironment.put(key, val);
            return self();
        }

        public T environmentVariables(Map<String,String> vars) {
            checkStillMutable();
            shellEnvironment.putAll(vars);
            return self();
        }

        @SuppressWarnings("unchecked")
        @Override
        public synchronized Task<RET> getTask() {
            if (task==null) {
                TaskBuilder<Object> tb = TaskBuilder.builder().dynamic(false).name("ssh: "+getSummary()).body(job);
                tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDIN, 
                        Streams.byteArrayOfString(Strings.join(commands, "\n"))));
                if (stdout!=null) tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDOUT, stdout));
                if (stderr!=null) tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDERR, stderr));
                task = tb.build();
            }
            return (Task<RET>) task;
        }
        
        public T summary(String summary) {
            checkStillMutable();
            this.summary = summary;
            return self();
        }

        public String getSummary() {
            if (summary!=null) return summary;
            return Strings.join(commands, " ; ");
        }
        
        protected SshJob newJob() {
            return new SshJob();
        }

        public <V> T configure(ConfigKey<V> key, V value) {
            config.configure(key, value);
            return self();
        }

        public class SshJob implements Callable<Object> {
            @Override
            public Object call() throws Exception {
                Preconditions.checkNotNull(getMachine(), "machine");
                
                ConfigBag config = ConfigBag.newInstanceCopying(AbstractSshTask.this.config);
                if (stdout!=null) config.put(SshTool.PROP_OUT_STREAM, stdout);
                if (stderr!=null) config.put(SshTool.PROP_ERR_STREAM, stderr);
                
                if (runAsRoot)
                    config.put(SshTool.PROP_RUN_AS_ROOT, true);

                if (runAsScript)
                    exitCode = getMachine().execScript(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
                else
                    exitCode = getMachine().execCommands(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
                
                if (requireExitCodeZero && exitCode!=0)
                    throw new IllegalStateException("Ssh job ended with exit code "+exitCode+" when 0 was required, in "+Tasks.current()+": "+getSummary());

                if (returnResultTransformation!=null)
                    return returnResultTransformation.apply(AbstractSshTask.this);
                
                switch (returnType) {
                case CUSTOM: return returnResultTransformation.apply(AbstractSshTask.this);
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
    public static class SshTask<RET> extends AbstractSshTask<SshTask<RET>,RET> {
        /** constructor where machine will be added later */
        public SshTask(String ...commands) {
            super(commands);
        }

        /** convenience constructor to supply machine immediately */
        public SshTask(SshMachineLocation machine, String ...commands) {
            this(commands);
            machine(machine);
        }

        @SuppressWarnings("unchecked")
        @Override
        public SshTask<String> requiringZeroAndReturningStdout() {
            return (SshTask<String>) super.requiringZeroAndReturningStdout();
        }

        @SuppressWarnings("unchecked")
        public <RET2> SshTask<RET2> returning(Function<SshTaskDetails, RET2> resultTransformation) {
            return (SshTask<RET2>) super.returning(resultTransformation);
        }
    }

    public static SshTask<Boolean> dontRequireTtyForSudo(SshMachineLocation machine, final boolean requireSuccess) {
        return newInstance(machine, CommonCommands.dontRequireTtyForSudo())
            .summary("setting up sudo")
            .configure(SshTool.PROP_ALLOCATE_PTY, true)
            .returning(new Function<SshTaskDetails,Boolean>() { public Boolean apply(SshTaskDetails task) {
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
