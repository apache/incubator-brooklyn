package brooklyn.entity.basic;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.HasTask;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;

public class SshTasks {

    public static SshTask<Integer> newInstance(String ...commands) {
        return new SshTask<Integer>(commands);
    }

    // cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
    public static class AbstractSshTask<T extends AbstractSshTask<T,?>,RET> implements HasTask<RET> {
        private final SshJob job;
        private final List<String> commands;
        private SshMachineLocation machine;
        private Task<Object> task;
        
        // config data
        private String summary;
        
        public static enum ScriptReturnType { EXIT_CODE, STDOUT_STRING, STDOUT_BYTES, STDERR_STRING, STDERR_BYTES }
        private ScriptReturnType returnType = ScriptReturnType.EXIT_CODE;
        
        private boolean runAsScript = false;
        private boolean runAsRoot = false;
        private boolean requireExitCodeZero = false;
        private Map<String,String> shellEnvironment = new MutableMap<String, String>();

        // execution details
        private ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private Integer exitCode = null;
        
        /** constructor where machine will be added later */
        public AbstractSshTask(String ...commands) {
            this.commands = Arrays.asList(commands);
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

        public SshMachineLocation getMachine() {
            return machine;
        }
            
        public T requiringExitCodeZero() {
            requireExitCodeZero = true;
            return self();
        }
        
        @SuppressWarnings({ "unchecked" })
        public AbstractSshTask<?,String> requiringZeroAndReturningStdout() {
            requiringExitCodeZero();
            return (AbstractSshTask<?,String>)returning(ScriptReturnType.STDOUT_STRING);
        }

        public AbstractSshTask<?,?> returning(ScriptReturnType type) {
            returnType = Preconditions.checkNotNull(type);
            return self();
        }

        public T runAsCommand() {
            runAsScript = false;
            return self();
        }

        public T runAsScript() {
            runAsScript = true;
            return self();
        }

        public T runAsRoot() {
            runAsRoot = true;
            return self();
        }
        
        public T environmentVariable(String key, String val) {
            shellEnvironment.put(key, val);
            return self();
        }

        public T environmentVariables(Map<String,String> vars) {
            shellEnvironment.putAll(vars);
            return self();
        }

        public Integer getExitCode() {
            return exitCode;
        }
        
        public byte[] getStdout() {
            if (stdout==null) return null;
            return stdout.toByteArray();
        }
        
        public byte[] getStderr() {
            if (stderr==null) return null;
            return stderr.toByteArray();
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
        
        public void summary(String summary) {
            checkStillMutable();
            this.summary = summary;
        }

        public String getSummary() {
            if (summary!=null) return summary;
            return Strings.join(commands, " ; ");
        }
        
        protected SshJob newJob() {
            return new SshJob();
        }
        
        public class SshJob implements Callable<Object> {
            @Override
            public Object call() throws Exception {
                Preconditions.checkNotNull(getMachine(), "machine");
                
                ConfigBag config = ConfigBag.newInstance();
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
                
                switch (returnType) {
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
    }

}
