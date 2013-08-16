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
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;

// this use of generics to get the right task type depending on subsequent builder methods is hideous,
// but seems to work; only thing is you can't instantiate SshTask directly, it must be a subclass
public class SshTask<T extends SshTask<T,?>,RET> implements HasTask<RET> {
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
    public SshTask(String ...commands) {
        this.commands = Arrays.asList(commands);
        this.job = newJob();
    }

    /** convenience constructor to supply machine immediately */
    public SshTask(SshMachineLocation machine, String ...commands) {
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
    public SshTask<?,String> requiringZeroAndReturningStdout() {
        requiringExitCodeZero();
        return (SshTask<?,String>)returning(ScriptReturnType.STDOUT_STRING);
    }

    public SshTask<?,?> returning(ScriptReturnType type) {
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
        return stdout.toByteArray();
    }
    
    public byte[] getStderr() {
        return stderr.toByteArray();
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized Task<RET> getTask() {
        if (task==null) 
            task = TaskBuilder.builder().dynamic(false).name("ssh: "+getSummary()).body(job).build();
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
    
    /** the "Plain" class exists purely so we can massage return types for callers' convenience */
    public static class SshTaskPlain<RET> extends SshTask<SshTaskPlain<RET>,RET> {
        /** constructor where machine will be added later */
        public SshTaskPlain(String ...commands) {
            super(commands);
        }

        /** convenience constructor to supply machine immediately */
        public SshTaskPlain(SshMachineLocation machine, String ...commands) {
            this(commands);
            machine(machine);
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public SshTaskPlain<String> requiringZeroAndReturningStdout() {
            return (SshTaskPlain<String>) super.requiringZeroAndReturningStdout();
        }
    }
}
