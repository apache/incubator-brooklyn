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

public class SshTask implements HasTask<Object> {
    final SshMachineLocation machine;
    final SshJob job;
    final List<String> commands;
    Task<Object> task;
    
    // config data
    String summary;
    
    public static enum ScriptReturnType { EXIT_CODE, STDOUT, STDERR }
    ScriptReturnType returnType = ScriptReturnType.EXIT_CODE;
    
    boolean runAsScript = false;
    boolean runAsRoot = false;
    boolean requireExitCodeZero = false;
    Map<String,String> shellEnvironment = new MutableMap<String, String>();

    // execution details
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    Integer exitCode = null;
    
    public SshTask(SshMachineLocation machine, String ...commands) {
        this.machine = machine;
        this.commands = Arrays.asList(commands);
        this.job = newJob();
    }

    public SshTask requiringExitCodeZero() {
        requireExitCodeZero = true;
        return this;
    }
    
    public SshTask requiringZeroAndReturningStdout() {
        requiringExitCodeZero();
        return returning(ScriptReturnType.STDOUT);
    }

    public SshTask returning(ScriptReturnType type) {
        returnType = Preconditions.checkNotNull(type);
        return this;
    }

    public SshTask runningAsCommand() {
        runAsScript = false;
        return this;
    }

    public SshTask runningAsScript() {
        runAsScript = true;
        return this;
    }

    public SshTask runningAsRoot() {
        runAsRoot = true;
        return this;
    }
    
    public SshTask environmentVariable(String key, String val) {
        shellEnvironment.put(key, val);
        return this;
    }

    public SshTask environmentVariables(Map<String,String> vars) {
        shellEnvironment.putAll(vars);
        return this;
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

    @Override
    public synchronized Task<Object> getTask() {
        if (task==null) 
            task = TaskBuilder.builder().dynamic(false).name("ssh: "+getSummary()).body(job).build();
        return task;
    }
    
    public void summary(String summary) {
        Preconditions.checkState(task==null, "Cannot set summary after task is gotten");
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
            ConfigBag config = ConfigBag.newInstance();
            if (stdout!=null) config.put(SshTool.PROP_OUT_STREAM, stdout);
            if (stderr!=null) config.put(SshTool.PROP_ERR_STREAM, stderr);
            
            if (runAsRoot)
                config.put(SshTool.PROP_RUN_AS_ROOT, true);

            if (runAsScript)
                exitCode = machine.execScript(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
            else
                exitCode = machine.execCommands(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
            
            if (requireExitCodeZero && exitCode!=0)
                throw new IllegalStateException("Ssh job ended with exit code "+exitCode+" when 0 was required, in "+Tasks.current()+": "+getSummary());
            
            if (returnType==ScriptReturnType.STDOUT) return stdout.toByteArray();
            if (returnType==ScriptReturnType.STDERR) return stderr.toByteArray();
            if (returnType==ScriptReturnType.EXIT_CODE) return exitCode;

            throw new IllegalStateException("Unknown return type for ssh job "+getSummary()+": "+returnType);
        }
    }
}
